package com.timelapse.backend.dto;

import java.util.List;

import com.timelapse.backend.types.FileChangeType;

public record FileChangeDto(
        String oldPath,
        String newPath,
        FileChangeType changeType,
        int additions,
        int deletions,
        boolean binary,
        List<DiffHunkDto> hunks
) {}