package com.timelapse.backend.service.git.metrics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.revwalk.RevCommit;

import com.timelapse.backend.dto.FileChangeDto;
import com.timelapse.backend.dto.RepositoryMetricsDto.FileMetricDto;

public final class FileHotspotMetric implements Metric {

    private static final class State {

        int changes;
        long add, del;
        Set<String> authors = new HashSet<>();
    }
    private final Map<String, State> files = new HashMap<>();

    public String name() {
        return "files";
    }

    public void accept(RevCommit c, List<FileChangeDto> changes, long add, long del) {
        for (FileChangeDto change : changes) {
            String path = MetricPaths.path(change);
            if (path == null) {
                continue;
            }
            State v = files.computeIfAbsent(path, k -> new State());
            v.changes++;
            v.add += change.additions();
            v.del += change.deletions();
            v.authors.add(c.getAuthorIdent().getName());
        }
    }

    public List<FileMetricDto> result() {
        return files.entrySet().stream().map(e -> {
            State v = e.getValue();
            return new FileMetricDto(e.getKey(), v.changes, v.add, v.del, v.add + v.del, v.authors.size(), v.changes * Math.log1p(v.add + v.del));
        }).sorted(Comparator.comparingDouble(FileMetricDto::hotspotScore).reversed().thenComparing(FileMetricDto::path)).toList();
    }
}
