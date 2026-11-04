package com.timelapse.backend.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record RepositoryMetricsDto(
        int totalCommits,
        int contributorCount,
        Instant firstCommitAt,
        Instant lastCommitAt,
        long repositoryAgeDays,
        long totalAdditions,
        long totalDeletions,
        long codeChurn,
        Map<String, Object> metricResults
        ) {

    public RepositoryMetricsDto {
        metricResults = Map.copyOf(metricResults);
    }

    @Override
    @JsonIgnore
    public Map<String, Object> metricResults() {
        return metricResults;
    }

    // Preserve existing flat API JSON keys for the frontend.
    @JsonAnyGetter
    public Map<String, Object> additionalMetrics() {
        return metricResults;
    }

    public record ActivityPointDto(String period, int commits, long additions, long deletions, long churn) {}

    public record ContributorMetricDto(String name, int commits, long additions, long deletions, long churn) {}

    public record FileMetricDto(String path, int changes, long additions, long deletions, long churn,
            int contributorCount, double hotspotScore) {}

    public record DirectoryMetricDto(String path, int changes, long additions, long deletions, long churn) {}

    public record FilePairMetricDto(String firstPath, String secondPath, int commitsTogether) {}
}
