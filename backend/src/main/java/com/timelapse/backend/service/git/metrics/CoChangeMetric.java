package com.timelapse.backend.service.git.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.revwalk.RevCommit;

import com.timelapse.backend.dto.FileChangeDto;
import com.timelapse.backend.dto.RepositoryMetricsDto.FilePairMetricDto;

public final class CoChangeMetric implements Metric {

    private final Map<String, Integer> pairs = new HashMap<>();

    public String name() {
        return "filesChangedTogether";
    }

    public void accept(RevCommit c, List<FileChangeDto> changes, long add, long del) {
        Set<String> unique = new HashSet<>();
        for (FileChangeDto change : changes) {
            String path = MetricPaths.path(change);
            if (path != null) {
                unique.add(path);
        
            }}
        List<String> paths = new ArrayList<>(unique);
        Collections.sort(paths);
        for (int i = 0; i < paths.size(); i++) {
            for (int j = i + 1; j < paths.size(); j++) {
                pairs.merge(paths.get(i) + "\u0000" + paths.get(j), 1, Integer::sum);
            }
        }
    }

    public List<FilePairMetricDto> result() {
        return pairs.entrySet().stream().filter(e -> e.getValue() > 1).map(e -> {
            String[] p = e.getKey().split("\u0000", 2);
            return new FilePairMetricDto(p[0], p[1], e.getValue());
        }).sorted(Comparator.comparingInt(FilePairMetricDto::commitsTogether).reversed().thenComparing(FilePairMetricDto::firstPath).thenComparing(FilePairMetricDto::secondPath)).limit(100).toList();
    }
}
