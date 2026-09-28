package com.timelapse.backend.controller;

import com.timelapse.backend.dto.GitHubInstallationDto;
import com.timelapse.backend.dto.GitHubInstallationRequest;
import com.timelapse.backend.dto.GitHubRepositoryDto;
import com.timelapse.backend.entity.GitHubInstallationEntity;
import com.timelapse.backend.service.github.GitHubInstallationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/github/installations")
public class GitHubConnectionController {
    private final GitHubInstallationService installationService;

    public GitHubConnectionController(GitHubInstallationService installationService) {
        this.installationService = installationService;
    }

    // Kept for manual/dev testing. It is already an upsert, so duplicate installation ids are safe.
    @PostMapping
    public GitHubInstallationDto registerInstallation(@RequestBody GitHubInstallationRequest request) {
        return toDto(installationService.upsert(
                request.installationId(),
                request.accountId(),
                request.accountLogin()
        ));
    }

    // Frontend endpoint: frontend only needs the installation_id GitHub redirected with.
    // The backend retrieves the trusted account id/login from GitHub and upserts the row.
    @PostMapping("/{installationId}/connect")
    public GitHubInstallationDto connect(@PathVariable Long installationId) throws Exception {
        return toDto(installationService.connectFromGitHub(installationId));
    }

    // Returns exactly the repositories currently authorized for this installation.
    @GetMapping("/{installationId}/repositories")
    public List<GitHubRepositoryDto> repositories(@PathVariable Long installationId) throws Exception {
        return installationService.getAccessibleRepositories(installationId);
    }

    private GitHubInstallationDto toDto(GitHubInstallationEntity entity) {
        return new GitHubInstallationDto(
                entity.getGithubInstallationId(),
                entity.getGithubAccountId(),
                entity.getGithubAccountLogin()
        );
    }
}
