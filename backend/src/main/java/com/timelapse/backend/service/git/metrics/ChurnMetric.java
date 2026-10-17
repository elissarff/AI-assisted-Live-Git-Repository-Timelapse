package com.timelapse.backend.service.git.metrics;

import java.util.List;

import org.eclipse.jgit.revwalk.RevCommit;

import com.timelapse.backend.dto.FileChangeDto;

public final class ChurnMetric implements Metric {

    private long additions, deletions;

    public String name() {
        return "codeChurn";
    }

    public void accept(RevCommit c, List<FileChangeDto> changes, long add, long del) {
        additions += add;
        deletions += del;
    }

    public Long result() {
        return additions + deletions;
    }

    public long additions() {
        return additions;
    }

    public long deletions() {
        return deletions;
    }
}
