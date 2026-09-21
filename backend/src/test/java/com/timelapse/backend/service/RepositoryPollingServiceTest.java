package com.timelapse.backend.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.types.MonitoringType;

class RepositoryPollingServiceTest {
    @Test
    void pollsOnlyPollingRepositoriesReturnedByRepositoryQuery() throws Exception {
        RepositoryJpaRepository repository = mock(RepositoryJpaRepository.class);
        RepositorySyncService syncService = mock(RepositorySyncService.class);
        RepositoryEntity polling = new RepositoryEntity();
        var idField = RepositoryEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(polling, 42L);
        when(repository.findAllByMonitoringType(MonitoringType.POLLING)).thenReturn(List.of(polling));

        new RepositoryPollingService(repository, syncService).pollPublicRepositories();

        verify(repository).findAllByMonitoringType(MonitoringType.POLLING);
        verify(syncService).sync(42L);
        verifyNoMoreInteractions(syncService);
    }
}
