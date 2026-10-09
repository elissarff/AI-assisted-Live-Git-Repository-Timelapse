package com.timelapse.backend.dto;

import java.time.Instant;
import java.util.List;

public record RepositoryMetricsDto(
    int totalCommits,
    int contributorCount,
    Instant firstCommitAt,
    Instant lastCommitAt,
    long repositoryAgeDays,
    long totalAdditions,
    long totalDeletions,
    long codeChurn,
    List<ActivityPointDto> activityOverTime,
    List<ContributorMetricDto> contributors,
    List<FileMetricDto> files,
    List<DirectoryMetricDto> directories,
    List<FilePairMetricDto> filesChangedTogether
) {
    public record ActivityPointDto(String period, int commits, long additions, long deletions, long churn) {}
    public record ContributorMetricDto(String name, int commits, long additions, long deletions, long churn) {}
    public record FileMetricDto(String path, int changes, long additions, long deletions, long churn,
                                int contributorCount, double hotspotScore) {}
    public record DirectoryMetricDto(String path, int changes, long additions, long deletions, long churn) {}
    public record FilePairMetricDto(String firstPath, String secondPath, int commitsTogether) {}
}
