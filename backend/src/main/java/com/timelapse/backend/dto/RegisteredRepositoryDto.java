package com.timelapse.backend.dto;

public record RegisteredRepositoryDto(
        String id,
        String name,
        String remoteUrl,
        String branch,
        String headSha
) {}