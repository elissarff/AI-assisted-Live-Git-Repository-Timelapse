package com.timelapse.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.timelapse.backend.dto.RepositoryInfoDto;
import com.timelapse.backend.service.RepositoryService;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(
            RepositoryService repositoryService
    ) {
        this.repositoryService = repositoryService;
    }

    @GetMapping("/inspect")
    public RepositoryInfoDto inspect(
            @RequestParam String path
    ) throws Exception {

        return repositoryService.inspect(path);
    }
}