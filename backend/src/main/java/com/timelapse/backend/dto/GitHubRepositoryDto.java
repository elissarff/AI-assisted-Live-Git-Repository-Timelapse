package com.timelapse.backend.dto;

public record GitHubRepositoryDto(
        Long providerRepositoryId,
        String fullName,
        String name,
        boolean privateRepository,
        String defaultBranch,
        String cloneUrl,
        String htmlUrl
) {}
