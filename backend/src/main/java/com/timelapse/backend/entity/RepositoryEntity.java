package com.timelapse.backend.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "repositories",
        indexes = {
            @Index(name = "idx_repositories_remote_url", columnList = "remote_url"),
            @Index(name = "idx_repositories_provider_repository_id", columnList = "provider_repository_id"),
            @Index(name = "idx_repositories_monitoring_type", columnList = "monitoring_type")
        }
)

public class RepositoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_key", nullable = false, unique = true)
    private UUID repoKey;

    @Column(name = "provider_repository_id", unique = true)
    private Long providerRepositoryId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "remote_url", nullable = false, unique = true, columnDefinition = "TEXT")
    private String remoteUrl;

    @Column(name = "default_branch")
    private String defaultBranch;

    @Column(name = "local_git_directory", nullable = false, columnDefinition = "TEXT")
    private String localGitDirectory;

    @Column(name = "last_processed_sha", length = 64)
    private String lastProcessedSha;

    @Enumerated(EnumType.STRING)
    @Column(name = "monitoring_type", nullable = false)
    private MonitoringType monitoringType = MonitoringType.POLLING;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private RepositoryVisibility visibility = RepositoryVisibility.PUBLIC;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "installation_id")
    private GitHubInstallationEntity installation;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (repoKey == null) repoKey = UUID.randomUUID();
        if (monitoringType == null) monitoringType = MonitoringType.POLLING;
        if (visibility == null) visibility = RepositoryVisibility.PUBLIC;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getRepoKey() { return repoKey; }
    public void setRepoKey(UUID repoKey) { this.repoKey = repoKey; }
    public Long getProviderRepositoryId() { return providerRepositoryId; }
    public void setProviderRepositoryId(Long providerRepositoryId) { this.providerRepositoryId = providerRepositoryId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getRemoteUrl() { return remoteUrl; }
    public void setRemoteUrl(String remoteUrl) { this.remoteUrl = remoteUrl; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public String getLocalGitDirectory() { return localGitDirectory; }
    public void setLocalGitDirectory(String localGitDirectory) { this.localGitDirectory = localGitDirectory; }
    public String getLastProcessedSha() { return lastProcessedSha; }
    public void setLastProcessedSha(String lastProcessedSha) { this.lastProcessedSha = lastProcessedSha; }
    public MonitoringType getMonitoringType() { return monitoringType; }
    public void setMonitoringType(MonitoringType monitoringType) { this.monitoringType = monitoringType; }
    public RepositoryVisibility getVisibility() { return visibility; }
    public void setVisibility(RepositoryVisibility visibility) { this.visibility = visibility; }
    public GitHubInstallationEntity getInstallation() { return installation; }
    public void setInstallation(GitHubInstallationEntity installation) { this.installation = installation; }
    public OffsetDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(OffsetDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
