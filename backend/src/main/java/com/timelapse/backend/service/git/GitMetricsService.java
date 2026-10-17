package com.timelapse.backend.service.git;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.timelapse.backend.dto.RepositoryMetricsDto;
import com.timelapse.backend.service.git.metrics.ActivityMetric;
import com.timelapse.backend.service.git.metrics.ChurnMetric;
import com.timelapse.backend.service.git.metrics.CoChangeMetric;
import com.timelapse.backend.service.git.metrics.ContributorMetric;
import com.timelapse.backend.service.git.metrics.DirectoryMetric;
import com.timelapse.backend.service.git.metrics.FileHotspotMetric;
import com.timelapse.backend.service.git.metrics.Metric;

/**
 * Single-pass metrics coordinator and name-based metric dispatcher.
 */
@Service
public class GitMetricsService {

    private static final Logger log = LoggerFactory.getLogger(GitMetricsService.class);
    private final GitDiffService diffs;
    private final ConcurrentHashMap<String, CompletableFuture<RepositoryMetricsDto>> cache = new ConcurrentHashMap<>();

    public GitMetricsService(GitDiffService diffs) {
        this.diffs = diffs;
    }

    /**
     * Name-based API. Use "all" for the existing combined response.
     */
    public Object calculate(Repository repository, ObjectId head, String metricName) throws Exception {
        RepositoryMetricsDto result = calculate(repository, head);
        return switch (metricName) {
            case "all" ->
                result;
            case "totalCommits" ->
                result.totalCommits();
            case "contributorCount" ->
                result.contributorCount();
            case "firstCommitAt" ->
                result.firstCommitAt();
            case "lastCommitAt" ->
                result.lastCommitAt();
            case "repositoryAgeDays" ->
                result.repositoryAgeDays();
            case "totalAdditions" ->
                result.totalAdditions();
            case "totalDeletions" ->
                result.totalDeletions();
            case "codeChurn" ->
                result.codeChurn();
            case "activityOverTime" ->
                result.activityOverTime();
            case "contributors" ->
                result.contributors();
            case "files" ->
                result.files();
            case "directories" ->
                result.directories();
            case "filesChangedTogether" ->
                result.filesChangedTogether();
            default ->
                throw new IllegalArgumentException("Unknown metric: " + metricName);
        };
    }

    /**
     * Caches by repository location and HEAD; concurrent identical requests
     * share the same calculation.
     */
    public RepositoryMetricsDto calculate(Repository repository, ObjectId head) throws Exception {
        if (head == null) {
            return empty();
        }
        String key = repository.getDirectory().getAbsolutePath() + ":" + head.name();
        CompletableFuture<RepositoryMetricsDto> future = new CompletableFuture<>();
        CompletableFuture<RepositoryMetricsDto> existing = cache.putIfAbsent(key, future);
        if (existing == null) {
            try {
                RepositoryMetricsDto value = compute(repository, head);
                future.complete(value);
                // Bound memory use across repeated HEAD updates.
                if (cache.size() > 32) {
                    cache.keySet().removeIf(k -> !k.equals(key));
                }
                return value;
            } catch (Throwable error) {
                future.completeExceptionally(error);
                cache.remove(key, future);
                if (error instanceof Exception e) {
                    throw e;
                }
                throw (Error) error;
            }
        }
        try {
            return existing.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    private RepositoryMetricsDto compute(Repository repository, ObjectId head) throws Exception {
        List<RevCommit> commits = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repository)) {
            walk.markStart(walk.parseCommit(head));
            for (RevCommit commit : walk) {
                commits.add(commit);
            }
        }
        commits.sort(Comparator.comparing(c -> c.getAuthorIdent().getWhenAsInstant()));
        ActivityMetric activity = new ActivityMetric();
        ContributorMetric contributors = new ContributorMetric();
        FileHotspotMetric files = new FileHotspotMetric();
        DirectoryMetric directories = new DirectoryMetric();
        CoChangeMetric pairs = new CoChangeMetric();
        ChurnMetric churn = new ChurnMetric();
        List<Metric> metrics = List.of(activity, contributors, files, directories, pairs, churn);
        long started = System.nanoTime();
        // A single JGit reader, formatter and walk avoids thousands of object-reader
        // allocations and repository rescans during the initial (uncached) request.
        try (ObjectReader reader = repository.newObjectReader(); RevWalk walk = new RevWalk(repository); DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(false);
            for (int i = 0; i < commits.size(); i++) {
                RevCommit commit = commits.get(i);
                var diff = diffs.getFileChangeStats(repository, commit, formatter, reader, walk);
                for (Metric metric : metrics) {
                    metric.accept(commit, diff.files(), diff.additions(), diff.deletions());
                }
                if ((i + 1) % 100 == 0 || i + 1 == commits.size()) {
                    log.info("Metrics progress {}/{} commits, {} ms", i + 1, commits.size(), (System.nanoTime() - started) / 1_000_000);
                }
            }
        }
        Instant first = commits.isEmpty() ? null : commits.get(0).getAuthorIdent().getWhenAsInstant();
        Instant last = commits.isEmpty() ? null : commits.get(commits.size() - 1).getAuthorIdent().getWhenAsInstant();
        return new RepositoryMetricsDto(commits.size(), contributors.result().size(), first, last,
                first == null ? 0 : Duration.between(first, last).toDays(),
                churn.additions(), churn.deletions(), churn.result(), activity.result(), contributors.result(),
                files.result(), directories.result(), pairs.result());
    }

    private RepositoryMetricsDto empty() {
        return new RepositoryMetricsDto(0, 0, null, null, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
