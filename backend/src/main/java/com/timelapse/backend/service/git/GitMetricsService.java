package com.timelapse.backend.service.git;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.stereotype.Service;

import com.timelapse.backend.dto.FileChangeDto;
import com.timelapse.backend.dto.RepositoryMetricsDto;
import com.timelapse.backend.dto.RepositoryMetricsDto.ActivityPointDto;
import com.timelapse.backend.dto.RepositoryMetricsDto.ContributorMetricDto;
import com.timelapse.backend.dto.RepositoryMetricsDto.DirectoryMetricDto;
import com.timelapse.backend.dto.RepositoryMetricsDto.FileMetricDto;
import com.timelapse.backend.dto.RepositoryMetricsDto.FilePairMetricDto;

/**
 * Calculates deterministic repository-evolution metrics directly from Git.
 */
@Service
public class GitMetricsService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
    private final GitDiffService diffs;

    public GitMetricsService(GitDiffService diffs) {
        this.diffs = diffs;
    }

    public RepositoryMetricsDto calculate(Repository repository, ObjectId branchHead) throws Exception {
        if (branchHead == null) {
            return empty();
        }
        List<RevCommit> commits = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repository)) {
            walk.markStart(walk.parseCommit(branchHead));
            for (RevCommit commit : walk) {
                commits.add(commit);
            }
        }
        commits.sort(Comparator.comparing(c -> c.getAuthorIdent().getWhenAsInstant()));

        Map<String, MutableActivity> activity = new HashMap<>();
        Map<String, MutableContributor> contributors = new HashMap<>();
        Map<String, MutableFile> files = new HashMap<>();
        Map<String, MutableDirectory> directories = new HashMap<>();
        Map<String, Integer> pairs = new HashMap<>();
        long totalAdd = 0, totalDelete = 0;

        for (RevCommit commit : commits) {
            var result = diffs.getFileChanges(repository, commit);
            long add = result.additions(), del = result.deletions();
            totalAdd += add;
            totalDelete += del;
            String author = commit.getAuthorIdent().getName();
            String period = MONTH.format(commit.getAuthorIdent().getWhenAsInstant());
            activity.computeIfAbsent(period, k -> new MutableActivity()).add(add, del);
            contributors.computeIfAbsent(author, k -> new MutableContributor()).add(add, del);

            Set<String> paths = new HashSet<>();
            for (FileChangeDto change : result.files()) {
                String path = change.newPath() != null && !"/dev/null".equals(change.newPath()) ? change.newPath() : change.oldPath();
                if (path == null || "/dev/null".equals(path)) {
                    continue;
                }
                paths.add(path);
                files.computeIfAbsent(path, k -> new MutableFile()).add(author, change.additions(), change.deletions());
                String directory = directoryOf(path);
                directories.computeIfAbsent(directory, k -> new MutableDirectory()).add(change.additions(), change.deletions());
            }
            List<String> changed = new ArrayList<>(paths);
            changed.sort(String::compareTo);
            for (int i = 0; i < changed.size(); i++) {
                for (int j = i + 1; j < changed.size(); j++) {
                    pairs.merge(changed.get(i) + "\u0000" + changed.get(j), 1, Integer::sum);
                }
            }
        }

        Instant first = commits.isEmpty() ? null : commits.get(0).getAuthorIdent().getWhenAsInstant();
        Instant last = commits.isEmpty() ? null : commits.get(commits.size() - 1).getAuthorIdent().getWhenAsInstant();
        long age = first == null || last == null ? 0 : Duration.between(first, last).toDays();

        List<ActivityPointDto> activityDtos = activity.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().dto(e.getKey())).toList();
        List<ContributorMetricDto> contributorDtos = contributors.entrySet().stream()
                .map(e -> e.getValue().dto(e.getKey())).sorted(Comparator.comparingInt(ContributorMetricDto::commits).reversed()).toList();
        List<FileMetricDto> fileDtos = files.entrySet().stream().map(e -> e.getValue().dto(e.getKey()))
                .sorted(Comparator.comparingDouble(FileMetricDto::hotspotScore).reversed()).toList();
        List<DirectoryMetricDto> directoryDtos = directories.entrySet().stream().map(e -> e.getValue().dto(e.getKey()))
                .sorted(Comparator.comparingInt(DirectoryMetricDto::changes).reversed()).toList();
        List<FilePairMetricDto> pairDtos = pairs.entrySet().stream().filter(e -> e.getValue() > 1).map(e -> {
            String[] p = e.getKey().split("\u0000", 2);
            return new FilePairMetricDto(p[0], p[1], e.getValue());
        }).sorted(Comparator.comparingInt(FilePairMetricDto::commitsTogether).reversed()).limit(100).toList();

        return new RepositoryMetricsDto(commits.size(), contributors.size(), first, last, age, totalAdd, totalDelete,
                totalAdd + totalDelete, activityDtos, contributorDtos, fileDtos, directoryDtos, pairDtos);
    }

    private RepositoryMetricsDto empty() {
        return new RepositoryMetricsDto(0, 0, null, null, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private String directoryOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "(root)" : path.substring(0, slash);
    }

    private static class MutableActivity {

        int commits;
        long add, del;

        void add(long a, long d) {
            commits++;
            add += a;
            del += d;
        }

        ActivityPointDto dto(String p) {
            return new ActivityPointDto(p, commits, add, del, add + del);
        }
    }

    private static class MutableContributor {

        int commits;
        long add, del;

        void add(long a, long d) {
            commits++;
            add += a;
            del += d;
        }

        ContributorMetricDto dto(String n) {
            return new ContributorMetricDto(n, commits, add, del, add + del);
        }
    }

    private static class MutableFile {

        int changes;
        long add, del;
        Set<String> contributors = new HashSet<>();

        void add(String c, long a, long d) {
            changes++;
            add += a;
            del += d;
            contributors.add(c);
        }

        FileMetricDto dto(String p) {
            double score = changes * Math.log1p(add + del);
            return new FileMetricDto(p, changes, add, del, add + del, contributors.size(), score);
        }
    }

    private static class MutableDirectory {

        int changes;
        long add, del;

        void add(long a, long d) {
            changes++;
            add += a;
            del += d;
        }

        DirectoryMetricDto dto(String p) {
            return new DirectoryMetricDto(p, changes, add, del, add + del);
        }
    }
}
