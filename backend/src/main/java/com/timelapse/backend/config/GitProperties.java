package com.timelapse.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GitProperties {

    private final String repositoriesDirectory;
    private final String appId;
    private final String privateKey;
    private final String gitHubWebhookSecret;
    private final String appSlug;

    public GitProperties(
            @Value("${app.git.repositories-directory}") String repositoriesDirectory,
            @Value("${github.app-id:}") String appId,
            @Value("${github.private-key:}") String privateKey,
            @Value("${github.webhook-secret}") String gitHubWebhookSecret,
            @Value("${github.app-slug:}") String appSlug
    ) {
        this.repositoriesDirectory = repositoriesDirectory;
        this.appId = appId;
        this.privateKey = privateKey;
        this.gitHubWebhookSecret = gitHubWebhookSecret;
        this.appSlug = appSlug;
    }

    public String getRepositoriesDirectory() {
        return repositoriesDirectory;
    }

    public String getAppId() {
        return appId;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getGitHubWebhookSecret() {
        return gitHubWebhookSecret;
    }

    public String getAppSlug() {
        return appSlug;
    }
}