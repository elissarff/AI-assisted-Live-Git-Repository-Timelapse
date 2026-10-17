package com.timelapse.backend.service.git.metrics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.revwalk.RevCommit;

import com.timelapse.backend.dto.FileChangeDto;
import com.timelapse.backend.dto.RepositoryMetricsDto.ContributorMetricDto;

public final class ContributorMetric implements Metric {

    private final Map<String, long[]> authors = new HashMap<>();

    public String name() {
        return "contributors";
    }

    public void accept(RevCommit c, List<FileChangeDto> changes, long add, long del) {
        long[] v = authors.computeIfAbsent(c.getAuthorIdent().getName(), k -> new long[3]);
        v[0]++;
        v[1] += add;
        v[2] += del;
    }

    public List<ContributorMetricDto> result() {
        return authors.entrySet().stream().map(e -> new ContributorMetricDto(e.getKey(), (int) e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[1] + e.getValue()[2]))
                .sorted(Comparator.comparingInt(ContributorMetricDto::commits).reversed().thenComparing(ContributorMetricDto::name)).toList();
    }
}
