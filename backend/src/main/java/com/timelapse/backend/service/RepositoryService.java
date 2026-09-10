package com.timelapse.backend.service;

import org.springframework.stereotype.Service;

import com.timelapse.backend.dto.RepositoryInfoDto;

@Service
public class RepositoryService {

    private final GitService gitService;

    public RepositoryService(
            GitService gitService
    ) {
        this.gitService = gitService;
    }

    public RepositoryInfoDto inspect(
            String path
    ) throws Exception {

        return gitService.inspectRepository(path);
    }
}