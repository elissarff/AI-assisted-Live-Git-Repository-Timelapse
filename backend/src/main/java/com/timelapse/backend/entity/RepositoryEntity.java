package com.timelapse.backend.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "repositories",
        indexes = {
                @Index(
                        name = "idx_repositories_remote_url",
                        columnList = "remote_url"
                )
        }
)
public class RepositoryEntity {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Column(
            name = "repo_key",
            nullable = false,
            unique = true
    )
    private UUID repoKey;

    @Column(
            name = "name",
            nullable = false
    )
    private String name;

    @Column(
            name = "remote_url",
            nullable = false,
            unique = true,
            columnDefinition = "TEXT"
    )
    private String remoteUrl;

    @Column(
            name = "default_branch"
    )
    private String defaultBranch;

    @Column(
            name = "local_git_directory",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String localGitDirectory;

    @Column(
            name = "last_processed_sha",
            length = 64
    )
    private String lastProcessedSha;

    @Column(
            name = "created_at",
            nullable = false
    )
    private OffsetDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {

        OffsetDateTime now =
                OffsetDateTime.now();

        if (repoKey == null) {
            repoKey = UUID.randomUUID();
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public RepositoryEntity() {
    }

    public Long getId() {
        return id;
    }

    public UUID getRepoKey() {
        return repoKey;
    }

    public void setRepoKey(UUID repoKey) {
        this.repoKey = repoKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(
            String remoteUrl
    ) {
        this.remoteUrl = remoteUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(
            String defaultBranch
    ) {
        this.defaultBranch = defaultBranch;
    }

    public String getLocalGitDirectory() {
        return localGitDirectory;
    }

    public void setLocalGitDirectory(
            String localGitDirectory
    ) {
        this.localGitDirectory =
                localGitDirectory;
    }

    public String getLastProcessedSha() {
        return lastProcessedSha;
    }

    public void setLastProcessedSha(
            String lastProcessedSha
    ) {
        this.lastProcessedSha =
                lastProcessedSha;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}