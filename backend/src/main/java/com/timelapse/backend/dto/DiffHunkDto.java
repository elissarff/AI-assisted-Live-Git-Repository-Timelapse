package com.timelapse.backend.dto;

import java.util.List;

public record DiffHunkDto(
        int oldStart,
        int oldLineCount,
        int newStart,
        int newLineCount,
        List<DiffLineDto> lines
) {}