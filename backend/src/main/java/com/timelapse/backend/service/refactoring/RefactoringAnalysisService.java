package com.timelapse.backend.service.refactoring;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RefactoringRepositorySettingsJpaRepository;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.git.GitRepositoryService;

@Service
public class RefactoringAnalysisService {

    private final RepositoryJpaRepository repositories;
    private final RefactoringRepositorySettingsJpaRepository settings;
    private final GitRepositoryService git;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "refactoring-miner-worker");
        t.setDaemon(true);
        return t;
    });

    public RefactoringAnalysisService(RepositoryJpaRepository repositories,
            RefactoringRepositorySettingsJpaRepository settings,
            GitRepositoryService git, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.repositories = repositories;
        this.settings = settings;
        this.git = git;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    private RepositoryEntity require(UUID key) {
        return repositories.findByRepoKey(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));
    }

    public Map<String, Object> request(UUID key, String sha, boolean explicit) throws Exception {
        RepositoryEntity repo = require(key);
        if (!explicit && !enabled(repo.getId())) {
            return Map.of("status", "DISABLED");
        }
        try (Repository repository = git.openRepository(Path.of(repo.getLocalGitDirectory())); RevWalk walk = new RevWalk(repository)) {
            var id = repository.resolve(sha);
            if (id == null || !id.name().equals(sha)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid commit SHA");
            }
            walk.parseCommit(id);
            // Ensure the requested SHA is reachable from the tracked default branch.
            var head = repository.resolve("refs/remotes/origin/" + repo.getDefaultBranch());
            if (head == null) {
                head = repository.resolve("refs/heads/" + repo.getDefaultBranch());
            }
            if (head == null || !walk.isMergedInto(walk.parseCommit(id), walk.parseCommit(head))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Commit not reachable from tracked branch");
            }
        }
        jdbc.update("INSERT INTO refactoring_jobs(repository_id,commit_sha,requested_explicitly) VALUES (?,?,?) "
                + "ON CONFLICT(repository_id,commit_sha) DO UPDATE SET requested_explicitly = refactoring_jobs.requested_explicitly OR EXCLUDED.requested_explicitly",
                repo.getId(), sha, explicit);
        dispatch();
        return job(key, sha);
    }

    public void enqueueFromSync(Long repositoryId, List<String> shas) {
        if (!enabled(repositoryId)) {
            return;
        }
        for (String sha : shas) {
            jdbc.update("INSERT INTO refactoring_jobs(repository_id,commit_sha) VALUES (?,?) ON CONFLICT DO NOTHING", repositoryId, sha);
        }
        dispatch();
    }

    private boolean enabled(Long id) {
        return settings.findById(id).map(s -> s.isEnabled()).orElse(false);
    }

    
    public Map<String, Object> job(UUID key, String sha) {
        RepositoryEntity repo = require(key);

        List<Map<String, Object>> jobs = jdbc.queryForList(
            """
            SELECT id, commit_sha, status, attempts, last_error,
                created_at, updated_at
            FROM refactoring_jobs
            WHERE repository_id = ? AND commit_sha = ?
            """,
            repo.getId(), sha
        );

        if (jobs.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No analysis for commit"
            );
        }

        Map<String, Object> result =
            new LinkedHashMap<>(jobs.get(0));

        List<Map<String, Object>> refactorings = jdbc.query(
            """
            SELECT id, parent_sha, type, description,
                before_locations::text AS before_locations,
                after_locations::text AS after_locations
            FROM detected_refactorings
            WHERE job_id = ?
            ORDER BY id
            """,
            (rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();

                item.put("id", rs.getLong("id"));
                item.put("parent_sha", rs.getString("parent_sha"));
                item.put("type", rs.getString("type"));
                item.put("description", rs.getString("description"));

                try {
                    String before = rs.getString("before_locations");
                    String after = rs.getString("after_locations");

                    item.put(
                        "before_locations",
                        before == null
                            ? List.of()
                            : mapper.readValue(before, List.class)
                    );

                    item.put(
                        "after_locations",
                        after == null
                            ? List.of()
                            : mapper.readValue(after, List.class)
                    );
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    throw new IllegalStateException(
                        "Failed to deserialize refactoring locations",
                        e
                    );
                }

                return item;
            },
            result.get("id")
        );

        result.put("refactorings", refactorings);
        return result;
    }


    public List<Map<String, Object>> list(UUID key, int limit, int offset) {
        RepositoryEntity repo = require(key);
        return jdbc.queryForList("SELECT commit_sha,status,attempts,last_error,updated_at FROM refactoring_jobs WHERE repository_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?", repo.getId(), Math.min(200, Math.max(1, limit)), Math.max(0, offset));
    }

    public void retry(UUID key, String sha) {
        RepositoryEntity repo = require(key);
        int count = jdbc.update("UPDATE refactoring_jobs SET status='PENDING', attempts=0,last_error=NULL,requested_explicitly=TRUE,updated_at=NOW() WHERE repository_id=? AND commit_sha=? AND status='FAILED'", repo.getId(), sha);
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed jobs can be retried");
        }
        dispatch();
    }

    @Scheduled(fixedDelay = 15000)
    public void dispatch() {
        worker.submit(this::processNext);
    }

    private void processNext() {
        // Atomic claim permits multiple application instances without duplicate execution.
        List<Map<String, Object>> claimed = jdbc.queryForList("WITH next AS (SELECT j.id FROM refactoring_jobs j "
                + "LEFT JOIN refactoring_repository_settings s ON s.repository_id=j.repository_id "
                + "WHERE (j.status='PENDING' OR (j.status='FAILED' AND j.attempts<3 AND j.updated_at < NOW()-INTERVAL '2 minutes') "
                + "OR (j.status='RUNNING' AND j.updated_at < NOW()-INTERVAL '30 minutes')) "
                + "AND (j.requested_explicitly OR COALESCE(s.enabled,FALSE)) ORDER BY j.created_at FOR UPDATE OF j SKIP LOCKED LIMIT 1) "
                + "UPDATE refactoring_jobs j SET status='RUNNING',attempts=attempts+1,updated_at=NOW() FROM next WHERE j.id=next.id "
                + "RETURNING j.id,j.repository_id,j.commit_sha");
        if (claimed.isEmpty()) {
            return;
        }
        Map<String, Object> item = claimed.get(0);
        long jobId = ((Number) item.get("id")).longValue();
        try {
            RepositoryEntity repo = repositories.findById(((Number) item.get("repository_id")).longValue()).orElseThrow();
            String sha = (String) item.get("commit_sha");
            // RefactoringMiner uses JGit directly; Git stays authoritative.
            try (Repository repository = git.openRepository(Path.of(repo.getLocalGitDirectory())); RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(repository.resolve(sha));
                if (commit.getParentCount() == 0) {
                    jdbc.update("DELETE FROM detected_refactorings WHERE job_id=?", jobId);
                } else {
                    // detectAtCommit analyzes the commit against its parent(s) in RefactoringMiner.
                    var miner = new GitHistoryRefactoringMinerImpl();
                    List<Refactoring> detected = new ArrayList<>();
                    miner.detectAtCommit(repository, sha, new RefactoringHandler() {
                        @Override
                        public void handle(String commitId, List<Refactoring> refactorings) {
                            detected.addAll(refactorings);
                        }

                        @Override
                        public void handleException(String commitId, Exception e) {
                            throw new IllegalStateException("RefactoringMiner failed at " + commitId, e);
                        }
                    });
                    jdbc.update("DELETE FROM detected_refactorings WHERE job_id=?", jobId);
                    String parent = commit.getParent(0).getName();
                    for (Refactoring r : detected) {
                        jdbc.update("INSERT INTO detected_refactorings(job_id,parent_sha,type,description,before_locations,after_locations) VALUES (?,?,?,?,?::jsonb,?::jsonb)",
                                jobId, parent, r.getRefactoringType().toString(), r.toString(),
                                mapper.writeValueAsString(r.leftSide()), mapper.writeValueAsString(r.rightSide()));
                    }
                }
            }
            jdbc.update("UPDATE refactoring_jobs SET status='COMPLETED',last_error=NULL,updated_at=NOW() WHERE id=?", jobId);
        } catch (Exception e) {
            String message = String.valueOf(e.getMessage());
            jdbc.update("UPDATE refactoring_jobs SET status='FAILED',last_error=?,updated_at=NOW() WHERE id=?", message.substring(0, Math.min(2000, message.length())), jobId);
        }
    }
}
