package com.timelapse.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.timelapse.backend.dto.CloneRepositoryRequest;
import com.timelapse.backend.dto.RegisteredRepositoryDto;
import com.timelapse.backend.dto.RepositoryInfoDto;
import com.timelapse.backend.dto.SyncResultDto;
import com.timelapse.backend.service.RepositoryService;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(
            RepositoryService repositoryService
    ) {
        this.repositoryService =
                repositoryService;
    }
    @PostMapping
    public RegisteredRepositoryDto register(
            @RequestBody CloneRepositoryRequest request
    ) throws Exception {

        System.out.println("REMOTE URL = " + request.remoteUrl());

        return repositoryService.register(
                request.remoteUrl()
        );
    }

    @GetMapping("/{id}")
    public RepositoryInfoDto inspect(
            @PathVariable String id
    ) throws Exception {

        return repositoryService.inspect(id);
    }

    @PostMapping("/{id}/sync")
    public SyncResultDto sync(
            @PathVariable String id
    ) throws Exception {

        return repositoryService.sync(id);
    }
}