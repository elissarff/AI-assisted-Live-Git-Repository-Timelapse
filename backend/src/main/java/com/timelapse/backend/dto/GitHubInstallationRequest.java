package com.timelapse.backend.dto;

public record GitHubInstallationRequest(
        Long installationId,
        Long accountId,
        String accountLogin
) {}
