package com.timelapse.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.timelapse.backend.entity.RepositoryEntity;

public interface RepositoryJpaRepository
        extends JpaRepository<
                RepositoryEntity,
                Long
        > {

    Optional<RepositoryEntity>
    findByRepoKey(UUID repoKey);

    Optional<RepositoryEntity>
    findByRemoteUrl(String remoteUrl);

    boolean existsByRemoteUrl(
            String remoteUrl
    );
}