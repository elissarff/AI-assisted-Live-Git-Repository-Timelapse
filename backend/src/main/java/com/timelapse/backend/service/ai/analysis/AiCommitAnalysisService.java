package com.timelapse.backend.service.ai.analysis;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelapse.backend.dto.CommitDetailDto;
import com.timelapse.backend.dto.FileChangeDto;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.ai.AiSettingsService;
import com.timelapse.backend.service.git.GitMiningService;

@Service
public class AiCommitAnalysisService {

    private final JdbcTemplate jdbc;
    private final RepositoryJpaRepository repositories;
    private final GitMiningService git;
    private final AiSettingsService settings;
    private final LlmProvider provider;
    private final ObjectMapper mapper;
    private final int dailyLimit;
    private static final String PROMPT = "commit-v1";
    private static final String SCHEMA = "1";
    private static final Set<String> CATEGORIES = Set.of("feature", "bugfix", "refactoring", "testing", "documentation", "maintenance", "performance", "security", "other");

    public AiCommitAnalysisService(JdbcTemplate jdbc, RepositoryJpaRepository repositories,
            GitMiningService git, AiSettingsService settings, LlmProvider provider,
            ObjectMapper mapper, @Value("${app.ai.max-daily-requests:20}") int dailyLimit) {
        this.jdbc = jdbc;
        this.repositories = repositories;
        this.git = git;
        this.settings = settings;
        this.provider = provider;
        this.mapper = mapper;
        this.dailyLimit = dailyLimit;
    }

    private RepositoryEntity repo(String key) {
        try {
            return repositories.findByRepoKey(UUID.fromString(key))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid repository key");
        }
    }

    private long profile() {
        jdbc.update("INSERT INTO ai_analysis_profiles(provider,model,prompt_version,schema_version) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                provider.provider(), provider.model(), PROMPT, SCHEMA);
        return jdbc.queryForObject("SELECT id FROM ai_analysis_profiles WHERE provider=? AND model=? AND prompt_version=? AND schema_version=?",
                Long.class, provider.provider(), provider.model(), PROMPT, SCHEMA);
    }

    public void enqueueFromSync(Long repoId, List<String> shas) {
        // AI work is explicit only. Sync and playback must never generate analyses.
    }

    private void queue(long repoId, long profile, String sha, int priority, boolean retry) {
        if (sha == null || !sha.matches("[a-fA-F0-9]{40}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid commit SHA");
        }
        jdbc.update("INSERT INTO ai_analysis_jobs(repository_id,profile_id,target_type,target_key,analysis_type,priority) VALUES (?,?,'COMMIT',?,'EXPLAIN',?) ON CONFLICT(repository_id,profile_id,target_type,target_key,analysis_type) DO NOTHING",
                repoId, profile, sha, priority);
        if (retry) {
            jdbc.update("UPDATE ai_analysis_jobs SET status='PENDING',next_attempt_at=now(),priority=?,error=NULL,attempts=0,updated_at=now() WHERE repository_id=? AND profile_id=? AND target_type='COMMIT' AND target_key=? AND status='FAILED'",
                    priority, repoId, profile, sha);
        }
    }

    public Map<String, Object> request(String repoKey, String sha, boolean retry) {
        var r = repo(repoKey);
        if (r.getVisibility() == null || !"PUBLIC".equals(r.getVisibility().name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Free-tier AI analysis is restricted to public repositories");
        }
        queue(r.getId(), profile(), sha, 100, retry);
        var current = status(repoKey, sha);
        if ("COMPLETED".equals(current.get("status")) || "FAILED".equals(current.get("status"))) {
            return current;
        }
        // A manual request claims this commit immediately instead of waiting for the scheduler.
        List<Long> claimed = jdbc.queryForList("UPDATE ai_analysis_jobs SET status='RUNNING',priority=100,attempts=attempts+1,leased_until=now()+interval '4 minutes',updated_at=now() WHERE repository_id=? AND profile_id=? AND target_type='COMMIT' AND target_key=? AND analysis_type='EXPLAIN' AND (status='PENDING' OR status='RUNNING' AND leased_until<now()) RETURNING id",
                Long.class, r.getId(), profile(), sha);
        if (claimed.isEmpty()) {
            current = status(repoKey, sha);
            if ("COMPLETED".equals(current.get("status")) || "FAILED".equals(current.get("status"))) {
                return current;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This commit is already being analyzed. Try again when it finishes.");
        }
        processJob(claimed.get(0), true);
        return status(repoKey, sha);
    }

    public Map<String, Object> status(String repoKey, String sha) {
        var r = repo(repoKey);
        if (sha == null || !sha.matches("[a-fA-F0-9]{40}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SHA");
        }
        var rows = jdbc.queryForList("SELECT j.status,j.error,a.result::text AS result,a.category FROM ai_analysis_jobs j JOIN ai_analysis_profiles p ON p.id=j.profile_id LEFT JOIN ai_analyses a ON a.id=j.analysis_id WHERE j.repository_id=? AND j.target_type='COMMIT' AND j.target_key=? AND p.provider=? AND p.model=? AND p.prompt_version=? AND p.schema_version=? ORDER BY j.id DESC LIMIT 1",
                r.getId(), sha, provider.provider(), provider.model(), PROMPT, SCHEMA);
        if (rows.isEmpty()) {
            return Map.of("status", "NOT_REQUESTED");
        }
        var row = rows.get(0);
        if (row.get("result") != null) {
            try {
                return Map.of("status", row.get("status"), "analysis", mapper.readValue((String) row.get("result"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                }));
            } catch (Exception ex) {
                throw new IllegalStateException("Stored AI result is invalid", ex);
            }
        }
        return Map.of("status", row.get("status"), "error", row.get("error") == null ? "" : row.get("error"));
    }

    private String evidence(String repoKey, String sha) throws Exception {
        RepositoryEntity r = repo(repoKey);
        CommitDetailDto d = git.getCommitDetail(Path.of(r.getLocalGitDirectory()), sha);
        StringBuilder b = new StringBuilder("Commit: ").append(d.sha()).append("\nMessage: ").append(d.message()).append("\n");
        int budget = 12000;
        for (FileChangeDto f : d.files()) {
            String path = f.newPath() != null ? f.newPath() : f.oldPath();
            String meta = "\nFile: " + path + " status=" + f.changeType() + " +" + f.additions() + " -" + f.deletions() + "\n";
            if (b.length() + meta.length() > budget) {
                break;
            }
            b.append(meta);
            if (f.binary() || f.hunks() == null) {
                continue;
            }
            for (var h : f.hunks()) {
                if (h.lines() == null) {
                    continue;
                }
                for (var line : h.lines()) {
                    String value = line.type() + " " + line.content() + "\n";
                    if (b.length() + value.length() > budget) {
                        b.append("[DIFF TRUNCATED]\n");
                        return b.toString();
                    }
                    b.append(value);
                }
            }
        }
        return b.toString();
    }

    private String hash(String evidence) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(evidence.getBytes(StandardCharsets.UTF_8)));
    }

    private void validate(JsonNode n) {
        if (!n.isObject()) {
            throw new IllegalArgumentException("AI output must be an object");
        }
        for (String key : List.of("summary", "purpose", "category", "significance")) {
            if (!n.path(key).isTextual() || n.path(key).asText().isBlank() || n.path(key).asText().length() > 2500) {
                throw new IllegalArgumentException("Missing or invalid field: " + key);
            }
        }
        if (!CATEGORIES.contains(n.path("category").asText())) {
            throw new IllegalArgumentException("Unknown category");
        }
        for (String key : List.of("affectedComponents", "tags")) {
            JsonNode values = n.path(key);
            if (!values.isArray() || values.size() > 20) {
                throw new IllegalArgumentException("Invalid array: " + key);
            }
            for (JsonNode v : values) {
                if (!v.isTextual() || v.asText().length() > 160) {
                    throw new IllegalArgumentException("Invalid " + key + " entry");
                }
            }
        }
    }

    // Legacy jobs are retained for inspection; automatic explanation generation is disabled.
    public void processNext() {
        if (dailyLimit <= 0) {
            return;
        }
        // Claim atomically across application instances; recover leases after crashes.
        List<Long> claimed = jdbc.queryForList("WITH next AS (SELECT id FROM ai_analysis_jobs WHERE (status='PENDING' AND next_attempt_at<=now() OR status='RUNNING' AND leased_until<now()) AND attempts<3 ORDER BY priority DESC,created_at LIMIT 1 FOR UPDATE SKIP LOCKED) UPDATE ai_analysis_jobs j SET status='RUNNING',attempts=attempts+1,leased_until=now()+interval '4 minutes',updated_at=now() FROM next WHERE j.id=next.id RETURNING j.id", Long.class);
        if (claimed.isEmpty()) {
            return;
        }
        processJob(claimed.get(0), false);
    }

    private void processJob(long id, boolean manual) {
        try {
            Map<String, Object> job = jdbc.queryForMap("SELECT repository_id,target_key,profile_id FROM ai_analysis_jobs WHERE id=?", id);
            long repoId = ((Number) job.get("repository_id")).longValue();
            RepositoryEntity repo = repositories.findById(repoId).orElseThrow();
            if (repo.getVisibility() == null || !"PUBLIC".equals(repo.getVisibility().name())) {
                throw new IllegalStateException("Private repository is not allowed");
            }
            // Turning AI off stops automatic jobs; manually requested jobs have priority 100.
            int priority = jdbc.queryForObject("SELECT priority FROM ai_analysis_jobs WHERE id=?", Integer.class, id);
            if (priority < 100 && !settings.get(repo.getRepoKey()).autoAnalyze()) {
                jdbc.update("UPDATE ai_analysis_jobs SET status='PENDING',next_attempt_at=now()+interval '1 day',leased_until=NULL WHERE id=?", id);
                return;
            }
            String sha = (String) job.get("target_key");
            String source = evidence(repo.getRepoKey().toString(), sha);
            String sourceHash = hash(source);
            long p = ((Number) job.get("profile_id")).longValue();
            List<Long> cached = jdbc.queryForList("SELECT id FROM ai_analyses WHERE repository_id=? AND profile_id=? AND target_type='COMMIT' AND target_key=? AND analysis_type='EXPLAIN' AND evidence_hash=?", Long.class, repoId, p, sha, sourceHash);
            long analysisId;
            if (!cached.isEmpty()) {
                analysisId = cached.get(0); 
            }else {
                Integer count = jdbc.queryForObject("SELECT count(*) FROM ai_usage WHERE provider=? AND created_at>=date_trunc('day',now())", Integer.class, provider.provider());
                if (count != null && count >= dailyLimit) {
                    if (manual) {
                        throw new IllegalStateException("Daily AI request limit reached. Try again tomorrow.");
                    }
                    jdbc.update("UPDATE ai_analysis_jobs SET status='PENDING',attempts=attempts-1,next_attempt_at=now()+interval '1 day',leased_until=NULL WHERE id=?", id);
                    return;
                }
                long started = System.nanoTime();
                // Count attempts even if provider returns an error, to cap free-tier requests.
                jdbc.update("INSERT INTO ai_usage(repository_id,job_id,provider,model) VALUES (?,?,?,?)", repoId, id, provider.provider(), provider.model());
                JsonNode result = provider.explain(source);
                validate(result);
                jdbc.update("INSERT INTO ai_analyses(repository_id,profile_id,target_type,target_key,analysis_type,evidence_hash,result,category) VALUES (?,?,'COMMIT',?,'EXPLAIN',?,?::jsonb,?) ON CONFLICT DO NOTHING",
                        repoId, p, sha, sourceHash, mapper.writeValueAsString(result), result.path("category").asText());
                analysisId = jdbc.queryForObject("SELECT id FROM ai_analyses WHERE repository_id=? AND profile_id=? AND target_type='COMMIT' AND target_key=? AND analysis_type='EXPLAIN' AND evidence_hash=?", Long.class, repoId, p, sha, sourceHash);
                jdbc.update("UPDATE ai_usage SET elapsed_ms=? WHERE job_id=?", Duration.ofNanos(System.nanoTime() - started).toMillis(), id);
            }
            jdbc.update("UPDATE ai_analysis_jobs SET status='COMPLETED',analysis_id=?,error=NULL,leased_until=NULL,updated_at=now() WHERE id=?", analysisId, id);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            jdbc.update("UPDATE ai_analysis_jobs SET status=CASE WHEN ? OR attempts>=3 THEN 'FAILED' ELSE 'PENDING' END,error=?,next_attempt_at=now()+(interval '10 minutes' * attempts),leased_until=NULL,updated_at=now() WHERE id=?", manual, message.substring(0, Math.min(300, message.length())), id);
        }
    }
}
