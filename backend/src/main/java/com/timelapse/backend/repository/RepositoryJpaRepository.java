package com.timelapse.backend.repository;

import com.timelapse.backend.entity.MonitoringType;
import com.timelapse.backend.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, Long> {
    Optional<RepositoryEntity> findByRepoKey(UUID repoKey);
    Optional<RepositoryEntity> findByRemoteUrl(String remoteUrl);
    Optional<RepositoryEntity> findByProviderRepositoryId(Long providerRepositoryId);
    Optional<RepositoryEntity> findByFullNameIgnoreCase(String fullName);
    List<RepositoryEntity> findAllByMonitoringType(MonitoringType monitoringType);
    boolean existsByRemoteUrl(String remoteUrl);
}
