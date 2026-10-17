package com.timelapse.backend.service.git.metrics;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.revwalk.RevCommit;

import com.timelapse.backend.dto.FileChangeDto;
import com.timelapse.backend.dto.RepositoryMetricsDto.ActivityPointDto;

public final class ActivityMetric implements Metric {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
    private final Map<String, long[]> periods = new TreeMap<>();

    public String name() {
        return "activityOverTime";
    }

    public void accept(RevCommit c, List<FileChangeDto> changes, long add, long del) {
        long[] v = periods.computeIfAbsent(MONTH.format(c.getAuthorIdent().getWhenAsInstant()), k -> new long[3]);
        v[0]++;
        v[1] += add;
        v[2] += del;
    }

    public List<ActivityPointDto> result() {
        return periods.entrySet().stream().map(e -> new ActivityPointDto(e.getKey(), (int) e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[1] + e.getValue()[2])).toList();
    }
}
