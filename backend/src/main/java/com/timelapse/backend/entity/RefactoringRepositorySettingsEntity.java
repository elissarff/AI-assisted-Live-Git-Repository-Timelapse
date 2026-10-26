package com.timelapse.backend.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "refactoring_repository_settings")
public class RefactoringRepositorySettingsEntity {

    @Id
    @Column(name = "repository_id")
    private Long repositoryId;
    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void created() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(Long value) {
        repositoryId = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }
}
