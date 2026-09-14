package com.timelapse.backend.service.github;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.timelapse.backend.entity.GitHubInstallationEntity;
import com.timelapse.backend.repository.GitHubInstallationJpaRepository;

@Service
public class GitHubInstallationService {
    private final GitHubInstallationJpaRepository repository;

    public GitHubInstallationService(GitHubInstallationJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public GitHubInstallationEntity upsert(Long installationId, Long accountId, String accountLogin) {

        if (installationId == null) throw new IllegalArgumentException("GitHub installation id is required");

        GitHubInstallationEntity entity = repository.findByGithubInstallationId(installationId)
                .orElseGet(GitHubInstallationEntity::new);

        entity.setGithubInstallationId(installationId);

        if (accountId != null) entity.setGithubAccountId(accountId);

        if (accountLogin != null && !accountLogin.isBlank()) entity.setGithubAccountLogin(accountLogin);

        return repository.save(entity);
    }

    public GitHubInstallationEntity require(Long installationId) {

        if (installationId == null) throw new IllegalArgumentException("GitHub installation id is required");

        return repository.findByGithubInstallationId(installationId)
                .orElseThrow(() -> new IllegalArgumentException("GitHub installation not found: " + installationId));
    }
}
