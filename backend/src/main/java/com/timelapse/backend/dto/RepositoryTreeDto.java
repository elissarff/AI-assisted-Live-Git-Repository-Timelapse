package com.timelapse.backend.dto;

/** Complete directory/file structure for one immutable Git commit. */
public record RepositoryTreeDto(
        String commitSha,
        RepositoryTreeNodeDto root
) {}
