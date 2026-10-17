package com.timelapse.backend.service.git.metrics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.revwalk.RevCommit;

import com.timelapse.backend.dto.FileChangeDto;
import com.timelapse.backend.dto.RepositoryMetricsDto.DirectoryMetricDto;

public final class DirectoryMetric implements Metric {

    private final Map<String, long[]> directories = new HashMap<>();

    public String name() {
        return "directories";
    }

    public void accept(RevCommit c, List<FileChangeDto> changes, long add, long del) {
        for (FileChangeDto change : changes) {
            String path = MetricPaths.path(change);
            if (path == null) {
                continue;
            }
            int slash = path.lastIndexOf('/');
            String directory = slash < 0 ? "(root)" : path.substring(0, slash);
            long[] v = directories.computeIfAbsent(directory, k -> new long[3]);
            v[0]++;
            v[1] += change.additions();
            v[2] += change.deletions();
        }
    }

    public List<DirectoryMetricDto> result() {
        return directories.entrySet().stream().map(e -> new DirectoryMetricDto(e.getKey(), (int) e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[1] + e.getValue()[2]))
                .sorted(Comparator.comparingInt(DirectoryMetricDto::changes).reversed().thenComparing(DirectoryMetricDto::path)).toList();
    }
}
