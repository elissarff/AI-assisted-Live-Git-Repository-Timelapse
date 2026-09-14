package com.timelapse.backend.entity;

public enum MonitoringType {
    WEBHOOK, // Custom for testing
    GITHUB_APP, // Main Usage for user's personnel repositories (public/private access)
    POLLING // Unowned public repo - unable to access webhooks
}
