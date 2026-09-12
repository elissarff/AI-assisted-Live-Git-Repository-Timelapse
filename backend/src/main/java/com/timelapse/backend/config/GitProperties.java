package com.timelapse.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GitProperties {

    private final String repositoriesDirectory;
    private final String gitHubUsername;
    private final String gitHubToken;
    private final String gitHubWebhookSecret;

    public GitProperties(
            @Value("${app.git.repositories-directory}") String repositoriesDirectory,
            @Value("${github.username}") String gitHubUsername,
            @Value("${github.token}") String gitHubToken,
            @Value("${github.webhook-secret}") String gitHubWebhookSecret
    ) {
        this.repositoriesDirectory = repositoriesDirectory;
        this.gitHubUsername = gitHubUsername;
        this.gitHubToken = gitHubToken;
        this.gitHubWebhookSecret = gitHubWebhookSecret;
    }

    public String getRepositoriesDirectory() {
        return repositoriesDirectory;
    }

    public String getGitHubUsername() {
        return gitHubUsername;
    }

    public String getGitHubToken() {
        return gitHubToken;
    }

    public String getGitHubWebhookSecret() {
        return gitHubWebhookSecret;
    }
}