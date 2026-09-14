package com.timelapse.backend.dto;

public record GitHubInstallationDto(
        Long installationId,
        Long accountId,
        String accountLogin
) {}
