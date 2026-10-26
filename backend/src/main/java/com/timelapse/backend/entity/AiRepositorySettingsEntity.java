package com.timelapse.backend.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_repository_settings")
public class AiRepositorySettingsEntity {

    @Id
    @Column(name = "repository_id")
    private Long repositoryId;
    @Column(name = "auto_analyze", nullable = false)
    private boolean autoAnalyze = false;
    @Column(name = "historical_backfill", nullable = false)
    private boolean historicalBackfill = false;
    @Column(name = "monthly_token_limit", nullable = false)
    private long monthlyTokenLimit = 200000;
    @Column(name = "max_auto_commits_per_day", nullable = false)
    private int maxAutoCommitsPerDay = 50;
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

    public boolean isAutoAnalyze() {
        return autoAnalyze;
    }

    public void setAutoAnalyze(boolean value) {
        autoAnalyze = value;
    }

    public boolean isHistoricalBackfill() {
        return historicalBackfill;
    }

    public void setHistoricalBackfill(boolean value) {
        historicalBackfill = value;
    }

    public long getMonthlyTokenLimit() {
        return monthlyTokenLimit;
    }

    public void setMonthlyTokenLimit(long value) {
        monthlyTokenLimit = value;
    }

    public int getMaxAutoCommitsPerDay() {
        return maxAutoCommitsPerDay;
    }

    public void setMaxAutoCommitsPerDay(int value) {
        maxAutoCommitsPerDay = value;
    }
}
