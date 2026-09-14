package com.timelapse.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SyncResultDto(
        String repositoryId,
        String previousSha,
        String currentSha,
        boolean changed,
        int commitsProcessed,
        int newCommitCount,
        List<CommitDto> newCommits,
        OffsetDateTime syncedAt
) {}
