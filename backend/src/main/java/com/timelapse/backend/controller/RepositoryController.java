package com.timelapse.backend.controller;

import com.timelapse.backend.dto.CloneRepositoryRequest;
import com.timelapse.backend.dto.RegisteredRepositoryDto;
import com.timelapse.backend.dto.RepositoryInfoDto;
import com.timelapse.backend.dto.SyncResultDto;
import com.timelapse.backend.service.RepositoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {
    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping
    public RegisteredRepositoryDto register(@RequestBody CloneRepositoryRequest request) throws Exception {
        return repositoryService.register(request);
    }

    @GetMapping("/{id}")
    public RepositoryInfoDto inspect(@PathVariable String id) throws Exception {
        return repositoryService.inspect(id);
    }

    @PostMapping("/{id}/sync")
    public SyncResultDto sync(@PathVariable String id) throws Exception {
        return repositoryService.sync(id);
    }
}
