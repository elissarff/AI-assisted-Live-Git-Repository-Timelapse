package com.timelapse.backend.dto;

import java.time.Instant;
import java.util.List;

public record CommitDetailDto(
    String sha,
    String shortSha,
    String message,
    AuthorDto author,
    Instant timestamp,
    List<String> parentShas,

    int filesChanged,
    int additions,
    int deletions,

    List<FileChangeDto> files
) {}