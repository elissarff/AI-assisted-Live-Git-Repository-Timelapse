package com.timelapse.backend.dto;

import com.timelapse.backend.entity.MonitoringType;
import com.timelapse.backend.entity.RepositoryVisibility;

public record RegisteredRepositoryDto(
        String id,
        String name,
        String fullName,
        String remoteUrl,
        String branch,
        String headSha,
        MonitoringType monitoringType,
        RepositoryVisibility visibility,
        Long providerRepositoryId,
        Long installationId
) {}
