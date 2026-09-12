package com.timelapse.backend.dto;

import java.util.List;

public record RepositoryInfoDto(
        String id,
        String name,
        String remoteUrl,
        String branch,
        String headSha,
        int commitCount,
        List<CommitDto> recentCommits
) {}