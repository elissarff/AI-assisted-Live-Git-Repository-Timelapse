package com.timelapse.backend.dto;

import java.time.Instant;

public record CommitSummaryDto(
    String sha,
    String shortSha,
    String message,
    AuthorDto author,
    Instant timestamp,
    int filesChanged,
    int additions,
    int deletions
) {}