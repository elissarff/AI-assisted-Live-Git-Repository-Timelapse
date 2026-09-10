package com.timelapse.backend.dto;

import java.util.List;

public record RepositoryInfoDto(
        String name,
        String branch,
        String headSha,
        int commitCount,
        List<CommitDto> recentCommits
) {}