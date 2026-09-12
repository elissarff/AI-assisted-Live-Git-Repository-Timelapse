package com.timelapse.backend.dto;

import java.util.List;

public record SyncResultDto(
    String repositoryId,
    String previousSha,
    String currentSha,
    int newCommitCount,
    List<CommitDto> newCommits
) {}