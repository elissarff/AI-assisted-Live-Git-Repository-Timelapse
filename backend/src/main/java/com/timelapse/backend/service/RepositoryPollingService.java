package com.timelapse.backend.service;

import com.timelapse.backend.entity.MonitoringType;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RepositoryPollingService {
    private static final Logger log = LoggerFactory.getLogger(RepositoryPollingService.class);
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final RepositorySyncService repositorySyncService;

    public RepositoryPollingService(RepositoryJpaRepository repositoryJpaRepository,
                                    RepositorySyncService repositorySyncService) {
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.repositorySyncService = repositorySyncService;
    }

    @Scheduled(fixedDelayString = "${app.repository.poll-interval-ms:300000}")
    public void pollPublicRepositories() {
        for (RepositoryEntity repository : repositoryJpaRepository.findAllByMonitoringType(MonitoringType.POLLING)) {
            try {
                var result = repositorySyncService.sync(repository.getId());
                log.info("Polling sync repository={} changed={} commitsProcessed={}",
                        repository.getId(), result.changed(), result.commitsProcessed());
            } catch (Exception ex) {
                log.warn("Polling sync failed for repository={}: {}", repository.getId(), ex.getMessage());
            }
        }
    }
}
