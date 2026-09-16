package com.timelapse.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "github_installations")
public class GitHubInstallationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_installation_id", nullable = false, unique = true)
    private Long githubInstallationId;

    @Column(name = "github_account_id")
    private Long githubAccountId;

    @Column(name = "github_account_login")
    private String githubAccountLogin;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getGithubInstallationId() { return githubInstallationId; }
    public void setGithubInstallationId(Long githubInstallationId) { this.githubInstallationId = githubInstallationId; }
    public Long getGithubAccountId() { return githubAccountId; }
    public void setGithubAccountId(Long githubAccountId) { this.githubAccountId = githubAccountId; }
    public String getGithubAccountLogin() { return githubAccountLogin; }
    public void setGithubAccountLogin(String githubAccountLogin) { this.githubAccountLogin = githubAccountLogin; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
