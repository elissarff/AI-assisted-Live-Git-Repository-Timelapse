package com.timelapse.backend.dto;

public record FileContentDto(
        String path,
        String commitSha,
        long size,
        boolean binary,
        String content
) {}