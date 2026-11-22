package com.timelapse.backend.service.refactoring;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RefactoringRepositorySettingsJpaRepository;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.git.GitRepositoryService;

import jakarta.annotation.PreDestroy;

@Service
public class RefactoringAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(RefactoringAnalysisService.class);

    private final RepositoryJpaRepository repositories;
    private final RefactoringRepositorySettingsJpaRepository settings;
    private final GitRepositoryService git;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final RefactoringDetector detector;
    private final java.util.List<AtomicBoolean> historySlots = new ArrayList<>();
    private final ExecutorService worker;

    private final AtomicBoolean manualDraining = new AtomicBoolean(false);
    private final ExecutorService manualWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "refactoring-manual-worker");
        t.setDaemon(true);
        return t;
    });

    // Keep renewals independent of both the miner and Spring's repository scheduler.
    private final ScheduledExecutorService claimRenewals = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "refactoring-claim-renewal");
        t.setDaemon(true);
        return t;
    });

    public RefactoringAnalysisService(RepositoryJpaRepository repositories,
            RefactoringRepositorySettingsJpaRepository settings,
            GitRepositoryService git, JdbcTemplate jdbc, ObjectMapper mapper,
            PlatformTransactionManager transactionManager,
            RefactoringDetector detector,
            @org.springframework.beans.factory.annotation.Value("${app.refactoring.history-workers:2}") int historyWorkers) {
        if (historyWorkers < 1 || historyWorkers > 8) throw new IllegalArgumentException("History workers must be between 1 and 8");
        worker = Executors.newFixedThreadPool(historyWorkers, r -> {
            Thread t = new Thread(r, "refactoring-history-worker"); t.setDaemon(true); return t;
        });
        for (int i = 0; i < historyWorkers; i++) historySlots.add(new AtomicBoolean());
        this.repositories = repositories;
        this.settings = settings;
        this.git = git;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.detector = detector;
    }

    @PreDestroy
    void stopWorkers() {
        worker.shutdownNow();
        manualWorker.shutdownNow();
        claimRenewals.shutdownNow();
    }

    private RepositoryEntity require(UUID key) {
        return repositories.findByRepoKey(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));
    }

    public Map<String, Object> request(UUID key, String sha, boolean explicit) throws Exception {
        RepositoryEntity repo = require(key);
        // ensures auto-enablement of the refactoring miner for this repository
        // manually requested works
        if (!explicit && !enabled(repo.getId())) {
            return Map.of("status", "DISABLED");
        }
        try (Repository repository = git.openRepository(Path.of(repo.getLocalGitDirectory())); RevWalk walk = new RevWalk(repository)) {
            var id = repository.resolve(sha);
            if (id == null || !id.name().equals(sha)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid commit SHA");
            }
            walk.parseCommit(id);
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

    // One request schedules the full reachable default-branch history. Jobs are cached by SHA.
    // The preferred commit and its neighbors are queued first; remaining commits follow in batches of ten.
    // Resolve the selected branch, never silently substitute another branch.
    private List<String> branchCommits(RepositoryEntity repo, String requestedBranch) throws Exception {
        String branch = requestedBranch == null || requestedBranch.isBlank()
                ? repo.getDefaultBranch() : requestedBranch;
        if (branch == null || branch.isBlank() || branch.contains("..") || branch.startsWith("/")
                || branch.endsWith("/") || branch.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid branch");
        }
        List<String> shas = new ArrayList<>();
        try (Repository repository = git.openRepository(Path.of(repo.getLocalGitDirectory()));
             Git jgit = new Git(repository)) {
            ObjectId head = repository.resolve("refs/remotes/origin/" + branch);
            if (head == null) head = repository.resolve("refs/heads/" + branch);
            if (head == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown branch: " + branch);
            for (RevCommit commit : jgit.log().add(head).call()) shas.add(commit.getName());
        }
        java.util.Collections.reverse(shas);
        return shas;
    }

    // Jobs are stored once per repository+SHA; each branch schedules only its reachable commits.
    public Map<String, Object> startHistory(UUID key, String branch, String preferredSha) throws Exception {
        RepositoryEntity repo = require(key);
        if (!enabled(repo.getId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Enable refactoring mode first");
        List<String> shas = branchCommits(repo, branch);
        if (preferredSha != null) {
            int position = shas.indexOf(preferredSha);
            if (position >= 0) {
                List<String> priority = new ArrayList<>();
                for (int distance = 0; distance <= 5; distance++) {
                    if (position - distance >= 0) priority.add(shas.get(position - distance));
                    if (distance > 0 && position + distance < shas.size()) priority.add(shas.get(position + distance));
                }
                Set<String> seen = new HashSet<>(priority);
                for (String sha : shas) if (seen.add(sha)) priority.add(sha);
                shas = priority;
            }
        }
        for (int start = 0; start < shas.size(); start += 10) {
            for (String sha : shas.subList(start, Math.min(start + 10, shas.size()))) {
                jdbc.update("INSERT INTO refactoring_jobs(repository_id,commit_sha) VALUES (?,?) ON CONFLICT DO NOTHING", repo.getId(), sha);
            }
        }
        dispatch();
        return coverage(key, branch);
    }

    // Report coverage for the selected branch, including commits not yet scheduled.
    // A job belonging exclusively to another branch never affects this branch's progress.
    public Map<String, Object> coverage(UUID key, String branch) throws Exception {
        RepositoryEntity repo = require(key);
        List<String> reachable = branchCommits(repo, branch);
        Set<String> reachableSet = new HashSet<>(reachable);
        List<Map<String, Object>> stored = jdbc.queryForList("""
            SELECT j.commit_sha, j.status, COUNT(r.id) AS refactoring_count,
                   COALESCE(string_agg(DISTINCT r.type, '|'), '') AS refactoring_types
            FROM refactoring_jobs j LEFT JOIN detected_refactorings r ON r.job_id=j.id
            WHERE j.repository_id=? GROUP BY j.id, j.commit_sha, j.status
            ORDER BY j.created_at, j.id
            """, repo.getId());
        List<Map<String, Object>> rows = new ArrayList<>();
        long completed = 0, failed = 0, pending = reachable.size();
        for (Map<String, Object> row : stored) {
            if (!reachableSet.contains((String) row.get("commit_sha"))) continue;
            rows.add(row);
            String status = (String) row.get("status");
            if ("COMPLETED".equals(status)) { completed++; pending--; }
            else if ("FAILED".equals(status)) { failed++; pending--; }
        }
        return Map.of("total", reachable.size(), "completed", completed,
                "failed", failed, "pending", pending, "commits", rows);
    }

    public void retry(UUID key, String sha) {
        retry(key, sha, false);
    }

    public void retry(UUID key, String sha, boolean longer) {
        RepositoryEntity repo = require(key);
        int count = jdbc.update("UPDATE refactoring_jobs SET status='PENDING', attempts=0,last_error=NULL,requested_explicitly=TRUE,timeout_seconds=CASE WHEN ? THEN 300 ELSE NULL END,updated_at=NOW() WHERE repository_id=? AND commit_sha=? AND status='FAILED'", longer, repo.getId(), sha);
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed jobs can be retried");
        }
        dispatch();
    }

    @Scheduled(fixedDelay = 15000)
    public void dispatch() {
        for (var slot : historySlots) drain(worker, slot, false);
        drain(manualWorker, manualDraining, true);
    }

    private void drain(ExecutorService executor, AtomicBoolean active, boolean manual) {
        if (!active.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                while (!executor.isShutdown() && processNext(manual)) { /* drain this queue */ }
            } catch (RuntimeException e) {
                log.error("Unable to drain refactoring jobs (manual={})", manual, e);
            } finally {
                active.set(false);
            }
        });
    }

    private static long elapsedMs(long startedNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private boolean processNext(boolean manual) {

        // Atomic claim permits multiple application instances without duplicate execution.
        /* can be replaced with database transactions 
            1. Delete old detections
            2. Insert all new detections
            3. Mark job COMPLETED
        */
        // Each attempt owns a renewable lease. A stopped process cannot renew it.
        UUID claimToken = UUID.randomUUID();
        List<Map<String, Object>> claimed = jdbc.queryForList("""
            WITH parameters AS (SELECT ?::boolean AS manual, ?::uuid AS token), repository_claim AS (
                UPDATE repositories r SET refactoring_claim_token=(SELECT token FROM parameters),
                    refactoring_lease_until=NOW()+INTERVAL '1 minute'
                WHERE r.id=(
                    SELECT candidate.id FROM repositories candidate
                    JOIN refactoring_repository_settings settings ON settings.repository_id=candidate.id AND settings.enabled
                    WHERE NOT (SELECT manual FROM parameters)
                        AND (candidate.refactoring_lease_until IS NULL OR candidate.refactoring_lease_until<=NOW())
                        AND EXISTS (SELECT 1 FROM refactoring_jobs work WHERE work.repository_id=candidate.id
                            AND NOT work.requested_explicitly AND (work.status='PENDING' OR
                                (work.status='RUNNING' AND COALESCE(work.lease_expires_at,work.updated_at+INTERVAL '1 minute')<=NOW())))
                    ORDER BY (SELECT MAX(previous.updated_at) FROM refactoring_jobs previous
                        WHERE previous.repository_id=candidate.id AND previous.attempts>0) NULLS FIRST, candidate.id
                    FOR UPDATE OF candidate SKIP LOCKED LIMIT 1
                ) AND (r.refactoring_lease_until IS NULL OR r.refactoring_lease_until<=NOW())
                RETURNING r.id
            ), repository_activity AS (
                SELECT repository_id, MAX(updated_at) AS last_served
                FROM refactoring_jobs WHERE attempts > 0 GROUP BY repository_id
            ), next AS (
                SELECT j.id FROM refactoring_jobs j
                LEFT JOIN repository_activity activity ON activity.repository_id = j.repository_id
                LEFT JOIN refactoring_repository_settings s ON s.repository_id = j.repository_id
                WHERE (j.status = 'PENDING'
                    OR (j.status = 'RUNNING' AND COALESCE(j.lease_expires_at, j.updated_at + INTERVAL '1 minute') <= NOW()))
                    AND j.requested_explicitly = (SELECT manual FROM parameters)
                    AND ((SELECT manual FROM parameters) OR j.repository_id IN (SELECT id FROM repository_claim))
                    AND (j.requested_explicitly OR COALESCE(s.enabled, FALSE))
                ORDER BY COALESCE(activity.last_served, '-infinity'::timestamptz), j.created_at, j.id
                FOR UPDATE OF j SKIP LOCKED LIMIT 1
            )
            UPDATE refactoring_jobs j
            SET status = 'RUNNING', attempts = attempts + 1, updated_at = NOW(),
                claim_token = (SELECT token FROM parameters), lease_expires_at = NOW() + INTERVAL '1 minute'
            FROM next WHERE j.id = next.id
            RETURNING j.id, j.repository_id, j.commit_sha, j.timeout_seconds
            """, manual, claimToken);
        if (claimed.isEmpty()) {
            return false;
        }
        Map<String, Object> item = claimed.get(0);
        long jobId = ((Number) item.get("id")).longValue();
        long repositoryId = ((Number) item.get("repository_id")).longValue();
        String sha = (String) item.get("commit_sha");
        var renewal = claimRenewals.scheduleWithFixedDelay(
                () -> renewClaim(jobId, claimToken), 10, 10, TimeUnit.SECONDS);
        long startedNanos = System.nanoTime();
        log.info("Refactoring analysis started: jobId={} repositoryId={} sha={}", jobId, repositoryId, sha);
        try {
            RepositoryEntity repo = repositories.findById(repositoryId).orElseThrow();
            Path gitDirectory = Path.of(repo.getLocalGitDirectory());
            RefactoringResult result = item.get("timeout_seconds") instanceof Number limit
                    ? detector.detect(gitDirectory, sha, limit.intValue()) : detector.detect(gitDirectory, sha);
            saveResults(jobId, claimToken, result);
        } catch (InterruptedException e) {
            // Leave the lease recoverable after shutdown instead of reporting a detector failure.
            Thread.currentThread().interrupt();
            log.info("Refactoring analysis interrupted: jobId={} sha={}", jobId, sha);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Refactoring analysis timed out: jobId={} sha={} elapsedMs={}", jobId, sha, elapsedMs(startedNanos));
            finishClaim(jobId, claimToken, "FAILED", "TIMEOUT: " + e.getMessage());
        } catch (Exception | LinkageError e) {
            log.error("Refactoring analysis failed: jobId={} repositoryId={} sha={} elapsedMs={}",
                    jobId, repositoryId, sha, elapsedMs(startedNanos), e);
            String message = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            try {
                finishClaim(jobId, claimToken, "FAILED",
                        message.substring(0, Math.min(2000, message.length())));
            } catch (Exception updateError) {
                log.error("Unable to persist failed status: jobId={} sha={}", jobId, sha, updateError);
            }
        } finally {
            renewal.cancel(false);
        }
        return true;
    }

    private void renewClaim(long jobId, UUID claimToken) {
        try {
            jdbc.update("UPDATE repositories SET refactoring_lease_until=NOW()+INTERVAL '1 minute' WHERE refactoring_claim_token=? AND refactoring_lease_until>NOW()", claimToken);
            jdbc.update("""
                UPDATE refactoring_jobs SET lease_expires_at = NOW() + INTERVAL '1 minute'
                WHERE id = ? AND status = 'RUNNING' AND claim_token = ? AND lease_expires_at > NOW()
                """, jobId, claimToken);
        } catch (RuntimeException e) {
            log.warn("Unable to renew refactoring claim for jobId={}", jobId, e);
        }
    }

    private void saveResults(long jobId, UUID claimToken, RefactoringResult result) {
        transactions.executeWithoutResult(transaction -> {
            // Lock and finish the current claim in the same transaction as its results.
            if (!finishClaim(jobId, claimToken, "COMPLETED", null)) {
                log.info("Discarding results for expired refactoring claim: jobId={}", jobId);
                return;
            }
            jdbc.update("DELETE FROM detected_refactorings WHERE job_id = ?", jobId);
            for (var refactoring : result.detections()) {
                jdbc.update("""
                    INSERT INTO detected_refactorings(job_id, parent_sha, type, description, before_locations, after_locations)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)
                    """, jobId, result.parentSha(), refactoring.type(), refactoring.description(),
                    refactoring.beforeLocations().toString(), refactoring.afterLocations().toString());
            }
        });
    }

    private boolean finishClaim(long jobId, UUID claimToken, String status, String error) {
        int finished = jdbc.update("""
            UPDATE refactoring_jobs
            SET status = ?, last_error = ?, updated_at = NOW(), claim_token = NULL, lease_expires_at = NULL
            WHERE id = ? AND status = 'RUNNING' AND claim_token = ? AND lease_expires_at > NOW()
            """, status, error, jobId, claimToken);
        if (finished == 1) jdbc.update("UPDATE repositories SET refactoring_claim_token=NULL,refactoring_lease_until=NULL WHERE refactoring_claim_token=?", claimToken);
        return finished == 1;
    }
}
