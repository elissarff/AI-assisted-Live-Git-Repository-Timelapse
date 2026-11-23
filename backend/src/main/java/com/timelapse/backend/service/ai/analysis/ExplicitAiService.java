package com.timelapse.backend.service.ai.analysis;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.git.GitMiningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExplicitAiService {

    static final Set<String> CATEGORIES = Set.of("feature", "bugfix", "refactoring", "testing", "documentation", "maintenance", "performance", "security", "other");
    private final JdbcTemplate jdbc;
    private final RepositoryJpaRepository repositories;
    private final GitMiningService git;
    private final LlmProvider provider;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final int dailyLimit;
    private final int fileMaxBytes;
    private final AiFileHistory fileHistory;

    public ExplicitAiService(JdbcTemplate jdbc, RepositoryJpaRepository repositories, GitMiningService git,
            LlmProvider provider, ObjectMapper mapper, PlatformTransactionManager manager,
            @Value("${app.ai.max-daily-requests:20}") int dailyLimit, AiFileHistory fileHistory,
            @Value("${app.ai.context.file-max-bytes:48000}") int fileMaxBytes) {
        this.jdbc = jdbc;
        this.repositories = repositories;
        this.git = git;
        this.provider = provider;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(manager);
        this.dailyLimit = dailyLimit;
        this.fileHistory = fileHistory;
        if (fileMaxBytes <= 0) throw new IllegalArgumentException("AI file read limit must be positive");
        this.fileMaxBytes = fileMaxBytes;
    }

    private RepositoryEntity repo(UUID key) {
        var r = repositories.findByRepoKey(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));
        if (r.getVisibility() == null || !"PUBLIC".equals(r.getVisibility().name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI is available for public repositories only");
        }
        return r;
    }

    private String profile() {
        return provider.provider() + ":" + provider.model() + ":explicit-v1";
    }

    static String bounded(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "\n[Evidence truncated]";
    }

    private String hash(String evidence) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(evidence.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, Object> plain(JsonNode value) {
        return mapper.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
    }

    private List<Map<String, Object>> cached(long repo, String type, String key) {
        return jdbc.queryForList("SELECT result::text AS result FROM ai_context_results WHERE repository_id=? AND target_type=? AND target_key=? AND profile=? ORDER BY id DESC LIMIT 1", repo, type, key, profile());
    }

    private JsonNode read(List<Map<String, Object>> rows) throws Exception {
        return mapper.readTree((String) rows.get(0).get("result"));
    }

    private void save(long repo, String type, String key, String evidence, JsonNode value) throws Exception {
        jdbc.update("INSERT INTO ai_context_results(repository_id,target_type,target_key,profile,evidence_hash,result) VALUES (?,?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING", repo, type, key, profile(), hash(evidence), mapper.writeValueAsString(value));
    }

    private JsonNode generate(long repo, String instructions, String evidence) throws Exception {
        // All explicit actions share a transaction-level provider lock: quota check + attempt
        // reservation are atomic across backend instances. Failed attempts still commit usage.
        jdbc.execute("SELECT pg_advisory_xact_lock(734921)");
        Integer used = jdbc.queryForObject("SELECT count(*) FROM ai_usage WHERE provider=? AND created_at>=date_trunc('day',now())", Integer.class, provider.provider());
        if (dailyLimit <= 0 || (used != null && used >= dailyLimit)) {
            throw new IllegalStateException("Daily AI limit reached");
        }
        jdbc.update("INSERT INTO ai_usage(repository_id,provider,model) VALUES (?,?,?)", repo, provider.provider(), provider.model());
        return provider.analyze(instructions, evidence);
    }

    private Map<String, Object> locked(UUID key, Work work) {
        var r = repo(key);
        return transactions.execute(status -> {
            jdbc.execute("SELECT pg_advisory_xact_lock(734921)");
            try {
                return work.run(r);
            } catch (Exception ex) {
                // Return an error value so an outbound attempt is not rolled back and forgotten.
                String message = ex.getMessage() == null ? "AI unavailable" : ex.getMessage();
                if (message.contains("503")) {
                    message = "AI is busy. Try again shortly."; 
                }else if (message.contains("429")) {
                    message = "AI quota reached. Try again later."; 
                }else if (ex instanceof java.net.http.HttpTimeoutException) {
                    message = "AI took too long. Try again."; 
                }else if (ex instanceof java.time.format.DateTimeParseException) {
                    message = "Choose a valid date range";
                }
                return Map.of("status", "FAILED", "error", bounded(message, 600));
            }
        });
    }

    @FunctionalInterface
    private interface Work {

        Map<String, Object> run(RepositoryEntity repo) throws Exception;
    }

    private void sha(RepositoryEntity repo, String sha) throws Exception {
        if (sha == null || !sha.matches("[a-fA-F0-9]{40}") || !git.commitExists(Path.of(repo.getLocalGitDirectory()), sha)) {
            throw new IllegalArgumentException("Invalid commit");
        }
    }

    public Map<String, Object> classifications(UUID key, String branch) throws Exception {
        var r = repo(key);
        String head = git.getRemoteBranchHead(Path.of(r.getLocalGitDirectory()), branch == null ? r.getDefaultBranch() : branch);
        if (head == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
        }
        var commits = git.getAllCommits(Path.of(r.getLocalGitDirectory()), head);
        Set<String> reachable = new HashSet<>();
        commits.forEach(c -> reachable.add(c.sha()));
        var result = new LinkedHashMap<String, Object>();
        for (var row : jdbc.queryForList("SELECT DISTINCT ON (target_key) target_key,result::text AS result FROM ai_context_results WHERE repository_id=? AND target_type='CLASSIFY' AND profile=? ORDER BY target_key,id DESC", r.getId(), profile())) {
            String id = (String) row.get("target_key");
            if (reachable.contains(id)) {
                result.put(id, plain(mapper.readTree((String) row.get("result"))));
            }
        }
        return Map.of("status", "COMPLETED", "total", commits.size(), "classified", result.size(), "commits", result);
    }

    public Map<String, Object> classify(UUID key, String branch) {
        return locked(key, r -> {
            var state = classifications(key, branch);
            @SuppressWarnings("unchecked")
            var done = (Map<String, Object>) state.get("commits");
            String head = git.getRemoteBranchHead(Path.of(r.getLocalGitDirectory()), branch == null ? r.getDefaultBranch() : branch);
            var missing = git.getAllCommits(Path.of(r.getLocalGitDirectory()), head).stream().filter(c -> !done.containsKey(c.sha())).limit(8).toList();
            if (missing.isEmpty()) {
                return state;
            }
            var needed = new ArrayList<com.timelapse.backend.dto.CommitDto>();
            for (var commit : missing) {
                var explanation = cached(r.getId(), "COMMIT", commit.sha());
                if (explanation.isEmpty()) {
                    explanation = jdbc.queryForList("SELECT a.result::text AS result FROM ai_analyses a JOIN ai_analysis_profiles p ON p.id=a.profile_id WHERE a.repository_id=? AND a.target_type='COMMIT' AND a.target_key=? AND p.provider=? AND p.model=? ORDER BY a.id DESC LIMIT 1", r.getId(), commit.sha(), provider.provider(), provider.model());
                }
                if (explanation.isEmpty()) {
                    needed.add(commit);
                    continue;
                }
                JsonNode value = read(explanation);
                var classification = mapper.createObjectNode();
                classification.put("sha", commit.sha());
                classification.put("summary", bounded(value.path("summary").asText(), 300));
                classification.set("category", value.path("category"));
                classification.set("tags", value.path("tags"));
                category(classification);
                tags(classification);
                save(r.getId(), "CLASSIFY", commit.sha(), value.toString(), classification);
            }
            if (needed.isEmpty()) {
                return classifications(key, branch);
            }
            missing = needed;
            StringBuilder evidence = new StringBuilder();
            for (var commit : missing) {
                evidence.append("SHA: ").append(commit.sha()).append("\n").append(commitEvidence(r, commit.sha(), 1800)).append("\n");
            }
            JsonNode response = generate(r.getId(), "Classify each supplied commit. Return JSON {commits:[{sha,summary,category,tags}]}. Exactly one entry per supplied SHA, no extra entries. summary is factual, at most 300 characters. tags contains at most 6 short strings. category must be one of " + CATEGORIES + ". Classification only, no detailed explanation.", evidence.toString());
            var entries = response.path("commits");
            if (!entries.isArray() || entries.size() != missing.size()) {
                throw new IllegalArgumentException("Incomplete classification response; retry");
            }
            Set<String> expected = new HashSet<>();
            missing.forEach(c -> expected.add(c.sha()));
            for (var entry : entries) {
                if (!expected.remove(entry.path("sha").asText())) {
                    throw new IllegalArgumentException("Unexpected classification SHA");
                }
                text(entry, "summary", 600);
                category(entry);
                tags(entry);
            }
            for (var entry : entries) {
                save(r.getId(), "CLASSIFY", entry.path("sha").asText(), evidence.toString(), entry);
            }
            return classifications(key, branch);
        });
    }

    public Map<String, Object> explain(UUID key, String type, String sha, String path) {
        return explain(key, type, sha, path, null, null);
    }

    public Map<String, Object> explain(UUID key, String type, String sha, String path, String from, String to) {
        return explain(key, type, sha, path, from, to, null, null);
    }

    public Map<String, Object> explain(UUID key, String type, String sha, String path, String from, String to, String fromSha, String toSha) {
        if (!Set.of("COMMIT", "FILE_SNAPSHOT", "REPOSITORY", "PERIOD", "FILE_CHANGE", "FILE_EVOLUTION").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported explanation");
        }
        return locked(key, r -> {
            sha(r, sha);
            if (type.startsWith("FILE_") && (path == null || path.isBlank() || path.startsWith("/") || Arrays.asList(path.split("/")).contains(".."))) {
                throw new IllegalArgumentException("Invalid file path");
            }
            java.time.LocalDate start = from == null || from.isBlank() ? null : java.time.LocalDate.parse(from);
            java.time.LocalDate end = to == null || to.isBlank() ? null : java.time.LocalDate.parse(to);
            boolean range = fromSha != null || toSha != null;
            if (range) {
                if (!type.equals("PERIOD") || start != null || end != null || fromSha == null || toSha == null) {
                    throw new IllegalArgumentException("Choose dates or two commits");
                }
                sha(r, fromSha);
                sha(r, toSha);
                var directory = Path.of(r.getLocalGitDirectory());
                if (!git.isAncestor(directory, fromSha, toSha) || !git.isAncestor(directory, toSha, sha)) {
                    throw new IllegalArgumentException("Range must follow ancestry on this branch");
                }
                if (fromSha.equals(toSha)) {
                    throw new IllegalArgumentException("Choose two different commits");
                }
            }
            if (type.equals("PERIOD") && !range && (start == null && end == null || start != null && end != null && end.isBefore(start))) {
                throw new IllegalArgumentException("Choose a valid date range");
            }
            String target = sha + (type.startsWith("FILE_") ? ":" + path : type.equals("PERIOD") ? range ? ":range:" + fromSha + ":" + toSha : ":dates:" + start + ":" + end : "");
            var hit = cached(r.getId(), type, target);
            if (!hit.isEmpty()) {
                return Map.of("status", "COMPLETED", "analysis", plain(read(hit)));
            }
            String evidence;
            if (type.equals("COMMIT")) {
                // Reuse compatible explanations generated before the explicit-action redesign.
                var legacy = jdbc.queryForList("SELECT a.result::text AS result FROM ai_analyses a JOIN ai_analysis_profiles p ON p.id=a.profile_id WHERE a.repository_id=? AND a.target_type='COMMIT' AND a.target_key=? AND p.provider=? AND p.model=? ORDER BY a.id DESC LIMIT 1", r.getId(), sha, provider.provider(), provider.model());
                if (!legacy.isEmpty()) {
                    return Map.of("status", "COMPLETED", "analysis", plain(read(legacy)));
                }
                evidence = commitEvidence(r, sha, 12000);
                var classification = cached(r.getId(), "CLASSIFY", sha);
                if (!classification.isEmpty()) {
                    evidence += "\nPrior classification (verify against Git): " + bounded((String) classification.get(0).get("result"), 1200);
                }
            } else if (type.equals("FILE_CHANGE") || type.equals("FILE_EVOLUTION")) {
                evidence = fileHistory.evidence(Path.of(r.getLocalGitDirectory()), sha, path, type.equals("FILE_EVOLUTION"));
            } else if (type.equals("FILE_SNAPSHOT")) {
                var file = git.getFileContent(Path.of(r.getLocalGitDirectory()), sha, path, fileMaxBytes);
                if (file.binary()) {
                    throw new IllegalArgumentException("Binary files cannot be explained");
                }
                evidence = "Revision: " + sha + "\nFile: " + path + "\n" + bounded(file.content(), 12000);
            } else if (type.equals("PERIOD")) {
                var commits = (range ? git.getCommitsBetween(Path.of(r.getLocalGitDirectory()), fromSha, toSha) : git.getAllCommits(Path.of(r.getLocalGitDirectory()), sha)).stream().filter(c -> {
                    var date = c.timestamp().atZone(java.time.ZoneOffset.UTC).toLocalDate();
                    return (start == null || !date.isBefore(start)) && (end == null || !date.isAfter(end));
                }).toList();
                if (commits.isEmpty()) {
                    throw new IllegalArgumentException("No commits in this period");
                }
                StringBuilder context = new StringBuilder("Repository: " + r.getName() + "\nBranch head: " + sha + "\nScope: " + (range ? "Commits (start excluded, end included): " + fromSha + " to " + toSha : "Inclusive UTC dates: " + start + " to " + end) + "\nMatching commits: " + commits.size() + "\n");
                var selected = commits.reversed().stream().limit(30).toList();
                context.append("Evidence covers the latest ").append(selected.size()).append(" matching commits. Disclose if this is a partial sample.\n");
                for (var c : selected) {
                    context.append("SHA: ").append(c.sha()).append(" Date: ").append(c.timestamp()).append("\n");
                    var summary = cached(r.getId(), "CLASSIFY", c.sha());
                    if (!summary.isEmpty()) {
                        context.append("Cached classification (verify against Git): ").append(bounded((String) summary.get(0).get("result"), 400)).append("\n");
                    }
                    context.append(commitEvidence(r, c.sha(), 350)).append("\n");
                }
                evidence = bounded(context.toString(), 16000);
            } else {
                var tree = git.getRepositoryTree(Path.of(r.getLocalGitDirectory()), sha);
                StringBuilder context = new StringBuilder("Repository: " + r.getName() + "\nRevision: " + sha + "\nPaths:\n");
                paths(tree.root(), context);
                for (String readme : List.of("README.md", "README", "readme.md")) {
                    try {
                        var file = git.getFileContent(Path.of(r.getLocalGitDirectory()), sha, readme, fileMaxBytes);
                        if (!file.binary()) {
                            context.append("\nREADME:\n").append(bounded(file.content(), 5000));

                        }
                        break;
                    } catch (Exception ignored) {
                        /* README is optional evidence. */ }
                }
                context.append("\nCached commit context:\n");
                for (var c : git.getAllCommits(Path.of(r.getLocalGitDirectory()), sha).reversed().stream().limit(12).toList()) {
                    var summary = cached(r.getId(), "CLASSIFY", c.sha());
                    context.append(c.sha()).append(" ").append(bounded(c.message(), 160));
                    if (!summary.isEmpty()) {
                        context.append(" ").append(bounded((String) summary.get(0).get("result"), 450));
                    }
                    context.append("\n");
                }
                evidence = bounded(context.toString(), 16000);
            }
            JsonNode result = generate(r.getId(), "Explain this " + type + " from the supplied evidence only. For FILE_SNAPSHOT describe the file role and behavior. For FILE_CHANGE explain only the selected file patch and its effect, including additions/deletions/renames. For FILE_EVOLUTION describe chronological changes across the supplied file history and cite commit SHA prefixes; disclose bounded history and uncertain rename links. For a repository describe purpose, organization and supported workflows; disclose partial evidence. For a PERIOD synthesize themes and changes across the supplied commits within the date bounds, not a single commit; state when the sample is partial. Return JSON with nonempty summary,purpose,significance,category,affectedComponents (string array),tags (string array). Category must be one of " + CATEGORIES + ". Be concise, do not invent behavior.", evidence);
            for (String field : List.of("summary", "purpose", "significance")) {
                text(result, field, 2500);
            }
            category(result);
            tags(result);
            if (!result.path("affectedComponents").isArray() || result.path("affectedComponents").size() > 20) {
                throw new IllegalArgumentException("Invalid components");
            }
            for (var value : result.path("affectedComponents")) {
                if (!value.isTextual() || value.asText().length() > 160) {
                    throw new IllegalArgumentException("Invalid component");
                }
            }
            save(r.getId(), type, target, evidence, result);
            return Map.of("status", "COMPLETED", "analysis", plain(result));
        });
    }

    private void paths(com.timelapse.backend.dto.RepositoryTreeNodeDto node, StringBuilder result) {
        if (result.length() > 6500) {
            return;
        }
        result.append(node.path()).append("\n");
        if (node.children() != null) {
            for (var child : node.children()) {
                paths(child, result);
            }
        }
    }

    private String commitEvidence(RepositoryEntity r, String sha, int limit) throws Exception {
        var d = git.getCommitDetail(Path.of(r.getLocalGitDirectory()), sha);
        StringBuilder b = new StringBuilder("Message: " + bounded(d.message(), 700) + "\n");
        for (var file : d.files()) {
            if (b.length() > limit) {
                break;
            }
            b.append(file.changeType()).append(" ").append(file.oldPath()).append(" -> ").append(file.newPath()).append(" +").append(file.additions()).append(" -").append(file.deletions()).append("\n");
            if (!file.binary() && file.hunks() != null) {
                for (var h : file.hunks()) {
                    if (b.length() > limit) {
                        break;
                    }
                    if (h.lines() != null) {
                        for (var line : h.lines()) {
                            if (b.length() > limit) {
                                break;

                            }
                            b.append(line.type()).append(" ").append(bounded(line.content(), 500)).append("\n");
                        }
                    }
                }
            }
        }
        return bounded(b.toString(), limit);
    }

    static void text(JsonNode node, String key, int limit) {
        if (!node.path(key).isTextual() || node.path(key).asText().isBlank() || node.path(key).asText().length() > limit) {
            throw new IllegalArgumentException("Invalid AI field: " + key);

        }
    }

    static void category(JsonNode node) {
        if (!CATEGORIES.contains(node.path("category").asText())) {
            throw new IllegalArgumentException("Invalid category");

        }
    }

    static void tags(JsonNode node) {
        if (!node.path("tags").isArray() || node.path("tags").size() > 20) {
            throw new IllegalArgumentException("Invalid tags");
        }
        for (var tag : node.path("tags")) {
            if (!tag.isTextual() || tag.asText().isBlank() || tag.asText().length() > 160) {
                throw new IllegalArgumentException("Invalid tag");
            }
        }
    }
}
