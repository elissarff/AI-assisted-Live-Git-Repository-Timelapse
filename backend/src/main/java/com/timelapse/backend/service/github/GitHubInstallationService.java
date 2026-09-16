package com.timelapse.backend.service.github;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.timelapse.backend.dto.GitHubInstallationDto;
import com.timelapse.backend.dto.GitHubRepositoryDto;
import com.timelapse.backend.entity.GitHubInstallationEntity;
import com.timelapse.backend.repository.GitHubInstallationJpaRepository;

@Service
public class GitHubInstallationService {
    private final GitHubInstallationJpaRepository repository;
    private final GitHubApiService gitHubApiService;

    public GitHubInstallationService(
            GitHubInstallationJpaRepository repository,
            GitHubApiService gitHubApiService
    ) {
        this.repository = repository;
        this.gitHubApiService = gitHubApiService;
    }

    @Transactional
    public GitHubInstallationEntity upsert(Long installationId, Long accountId, String accountLogin) {
        if (installationId == null) {
            throw new IllegalArgumentException("GitHub installation id is required");
        }

        GitHubInstallationEntity entity = repository.findByGithubInstallationId(installationId)
                .orElseGet(() -> {
                    if (accountId != null) {
                        return repository.findFirstByGithubAccountId(accountId)
                                .orElseGet(GitHubInstallationEntity::new);
                    }
                    return new GitHubInstallationEntity();
                });

        // an update to an existing GitHub installation is not an error.
        // If the app was uninstalled/reinstalled for the same GitHub account, this also
        // lets the stored row move to the new installation id.
        entity.setGithubInstallationId(installationId);
        if (accountId != null) entity.setGithubAccountId(accountId);
        if (accountLogin != null && !accountLogin.isBlank()) entity.setGithubAccountLogin(accountLogin);
        return repository.save(entity);
    }

    @Transactional
    public GitHubInstallationEntity connectFromGitHub(Long installationId) throws Exception {
        GitHubInstallationDto githubInstallation = gitHubApiService.getInstallation(installationId);
        return upsert(
                githubInstallation.installationId(),
                githubInstallation.accountId(),
                githubInstallation.accountLogin()
        );
    }

    public List<GitHubRepositoryDto> getAccessibleRepositories(Long installationId) throws Exception {
        // Confirm we have/refresh the installation record first. Calling this repeatedly is safe.
        connectFromGitHub(installationId);
        return gitHubApiService.getInstallationRepositories(installationId);
    }

    public GitHubInstallationEntity require(Long installationId) {
        return repository.findByGithubInstallationId(installationId)
                .orElseThrow(() -> new IllegalArgumentException("GitHub installation not found: " + installationId));
    }
}
