package com.timelapse.backend.dto;

import java.time.Instant;

public record CommitDto(
        String sha,
        String message,
        String author,
        Instant timestamp
) {}