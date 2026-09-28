package com.timelapse.backend.dto;

import com.timelapse.backend.types.MonitoringType;
import com.timelapse.backend.types.RepositoryVisibility;

public record CloneRepositoryRequest(
        String remoteUrl,
        MonitoringType monitoringType,
        Long providerRepositoryId,
        String fullName,
        String defaultBranch,
        RepositoryVisibility visibility,
        Long installationId
) {}
