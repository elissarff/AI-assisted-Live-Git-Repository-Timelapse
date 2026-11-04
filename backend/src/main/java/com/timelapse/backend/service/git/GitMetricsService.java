package com.timelapse.backend.service.git;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
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
import com.timelapse.backend.service.git.metrics.Metric;
import com.timelapse.backend.service.git.metrics.MetricRegistry;

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
        if (metricName.equals("all")) return result;
        if (result.metricResults().containsKey(metricName)) {
            return result.metricResults().get(metricName);
        }
        // Summary properties are record components, not metric implementations.
        for (var component : RepositoryMetricsDto.class.getRecordComponents()) {
            if (component.getName().equals(metricName) && !metricName.equals("metricResults")) {
                return component.getAccessor().invoke(result);
            }
        }
        throw new IllegalArgumentException("Unknown metric: " + metricName);
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

    private final ConcurrentHashMap<String, CompletableFuture<Object>> individualCache = new ConcurrentHashMap<>();

    /** Independent metric computation: a slow diagram never blocks another one. */
    public Object calculateIndividual(Repository repository, ObjectId head, String name) throws Exception {
        if (!name.equals("summary")) {
            MetricRegistry.createByName(name); // validate the metric name centrally
        }
        if (head == null) return name.equals("summary") ? java.util.Map.of("totalCommits", 0, "contributorCount", 0, "repositoryAgeDays", 0, "totalAdditions", 0, "totalDeletions", 0, "codeChurn", 0) : List.of();
        String key = repository.getDirectory().getAbsolutePath() + ":" + head.name() + ":" + name;
        CompletableFuture<Object> pending = new CompletableFuture<>();
        CompletableFuture<Object> existing = individualCache.putIfAbsent(key, pending);
        if (existing != null) {
            try { return existing.get(); }
            catch (ExecutionException e) { if (e.getCause() instanceof Exception cause) throw cause; throw new IllegalStateException(e.getCause()); }
        }
        try {
            Object result = computeIndividual(repository, head, name);
            pending.complete(result);
            if (individualCache.size() > 128) individualCache.keySet().removeIf(k -> !k.equals(key));
            return result;
        } catch (Throwable e) {
            pending.completeExceptionally(e);
            individualCache.remove(key, pending);
            if (e instanceof Exception ex) throw ex;
            throw (Error)e;
        }
    }

    private Object computeIndividual(Repository repository, ObjectId head, String name) throws Exception {
        List<RevCommit> commits = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repository)) {
            walk.markStart(walk.parseCommit(head));
            for (RevCommit commit : walk) commits.add(commit);
        }
        commits.sort(Comparator.comparing(c -> c.getAuthorIdent().getWhenAsInstant()));
        if (name.equals("summary")) {
            java.util.Set<String> authors = new java.util.HashSet<>();
            for (RevCommit c : commits) authors.add(c.getAuthorIdent().getName() + ":" + c.getAuthorIdent().getEmailAddress());
            Instant first = commits.isEmpty() ? null : commits.get(0).getAuthorIdent().getWhenAsInstant();
            Instant last = commits.isEmpty() ? null : commits.get(commits.size()-1).getAuthorIdent().getWhenAsInstant();
            // Summary totals are independent of any concrete metric class.
            long additions = 0, deletions = 0;
            try (ObjectReader reader = repository.newObjectReader(); RevWalk walk = new RevWalk(repository); DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                formatter.setRepository(repository);
                formatter.setDetectRenames(false);
                for (RevCommit c : commits) {
                    var diff = diffs.getFileChangeStats(repository, c, formatter, reader, walk);
                    additions += diff.additions();
                    deletions += diff.deletions();
                }
            }
            java.util.Map<String,Object> summary = new java.util.HashMap<>();
            summary.put("totalCommits", commits.size()); summary.put("contributorCount", authors.size());
            summary.put("firstCommitAt", first); summary.put("lastCommitAt", last);
            summary.put("repositoryAgeDays", first == null ? 0 : Duration.between(first,last).toDays());
            summary.put("totalAdditions", additions); summary.put("totalDeletions", deletions); summary.put("codeChurn", additions + deletions);
            return summary;
        }
        Metric metric = MetricRegistry.createByName(name);
        try (ObjectReader reader = repository.newObjectReader(); RevWalk walk = new RevWalk(repository); DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(false);
            for (RevCommit c : commits) {
                var diff = diffs.getFileChangeStats(repository, c, formatter, reader, walk);
                metric.accept(c, diff.files(), diff.additions(), diff.deletions());
            }
        }
        return metric.result();
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
        List<Metric> metrics = new ArrayList<>(MetricRegistry.createAll().values());
        Set<String> authors = new HashSet<>();
        long totalAdditions = 0, totalDeletions = 0;
        long started = System.nanoTime();
        // A single JGit reader, formatter and walk avoids thousands of object-reader
        // allocations and repository rescans during the initial (uncached) request.
        try (ObjectReader reader = repository.newObjectReader(); RevWalk walk = new RevWalk(repository); DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(false);
            for (int i = 0; i < commits.size(); i++) {
                RevCommit commit = commits.get(i);
                var diff = diffs.getFileChangeStats(repository, commit, formatter, reader, walk);
                authors.add(commit.getAuthorIdent().getName());
                totalAdditions += diff.additions();
                totalDeletions += diff.deletions();
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
        return new RepositoryMetricsDto(commits.size(), authors.size(), first, last,
                first == null ? 0 : Duration.between(first, last).toDays(),
                totalAdditions, totalDeletions, totalAdditions + totalDeletions,
                MetricRegistry.results(metrics));
    }

    private RepositoryMetricsDto empty() {
        return new RepositoryMetricsDto(0, 0, null, null, 0, 0, 0, 0, Map.of());
    }
}
