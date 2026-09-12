package com.timelapse.backend.model;

import java.nio.file.Path;

public class RegisteredRepository {

    private final String id;
    private final String name;
    private final String remoteUrl;
    private final Path gitDirectory;

    private String defaultBranch;
    private String lastProcessedSha;

    public RegisteredRepository(
            String id,
            String name,
            String remoteUrl,
            Path gitDirectory,
            String defaultBranch,
            String lastProcessedSha
    ) {
        this.id = id;
        this.name = name;
        this.remoteUrl = remoteUrl;
        this.gitDirectory = gitDirectory;
        this.defaultBranch = defaultBranch;
        this.lastProcessedSha = lastProcessedSha;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public Path getGitDirectory() {
        return gitDirectory;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getLastProcessedSha() {
        return lastProcessedSha;
    }

    public void setLastProcessedSha(String lastProcessedSha) {
        this.lastProcessedSha = lastProcessedSha;
    }
}