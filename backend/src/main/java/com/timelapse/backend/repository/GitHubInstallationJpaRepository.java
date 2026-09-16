package com.timelapse.backend.repository;

import com.timelapse.backend.entity.GitHubInstallationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitHubInstallationJpaRepository extends JpaRepository<GitHubInstallationEntity, Long> {
    Optional<GitHubInstallationEntity> findByGithubInstallationId(Long githubInstallationId);
    Optional<GitHubInstallationEntity> findFirstByGithubAccountId(Long githubAccountId);
}
