package com.timelapse.backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.timelapse.backend.dto.GitHubInstallationDto;
import com.timelapse.backend.dto.GitHubInstallationRequest;
import com.timelapse.backend.entity.GitHubInstallationEntity;
import com.timelapse.backend.service.github.GitHubInstallationService;

@RestController
@RequestMapping("/api/github/installations")
public class GitHubConnectionController {
    private final GitHubInstallationService installationService;

    public GitHubConnectionController(GitHubInstallationService installationService) {
        this.installationService = installationService;
    }

    @PostMapping
    public GitHubInstallationDto registerInstallation(@RequestBody GitHubInstallationRequest request) {
        GitHubInstallationEntity entity = installationService.upsert(
                request.installationId(), request.accountId(), request.accountLogin());
        return new GitHubInstallationDto(
            entity.getGithubInstallationId(), 
            entity.getGithubAccountId(), 
            entity.getGithubAccountLogin());
    }
}
