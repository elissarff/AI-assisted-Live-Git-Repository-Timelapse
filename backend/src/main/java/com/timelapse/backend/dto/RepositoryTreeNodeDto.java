package com.timelapse.backend.dto;

import java.util.List;

/** A node in the repository snapshot at one concrete Git commit. */
public record RepositoryTreeNodeDto(
        String name,
        String path,
        String type,
        List<RepositoryTreeNodeDto> children
) {}
