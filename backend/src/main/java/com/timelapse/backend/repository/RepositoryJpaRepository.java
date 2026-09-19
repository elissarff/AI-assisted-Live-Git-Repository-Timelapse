package com.timelapse.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.types.MonitoringType;

public interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, Long> {
    Optional<RepositoryEntity> findByRepoKey(UUID repoKey);
    Optional<RepositoryEntity> findByRemoteUrl(String remoteUrl);
    Optional<RepositoryEntity> findByProviderRepositoryId(Long providerRepositoryId);
    Optional<RepositoryEntity> findByFullNameIgnoreCase(String fullName);
    List<RepositoryEntity> findAllByMonitoringType(MonitoringType monitoringType);
    boolean existsByRemoteUrl(String remoteUrl);
}
