package com.timelapse.backend.dto;

import com.timelapse.backend.types.DiffLineType;

public record DiffLineDto(
        DiffLineType type,
        Integer oldLineNumber,
        Integer newLineNumber,
        String content
) {}