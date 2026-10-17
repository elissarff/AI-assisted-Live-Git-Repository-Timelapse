package com.timelapse.backend.service.git.metrics;

import java.util.List;

import org.eclipse.jgit.revwalk.RevCommit;

import com.timelapse.backend.dto.FileChangeDto;

/** Each metric receives the same single-pass commit stream. */
public interface Metric {
    String name();
    void accept(RevCommit commit, List<FileChangeDto> changes, long additions, long deletions);
    Object result();
}
