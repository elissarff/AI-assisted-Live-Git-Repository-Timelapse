package com.timelapse.backend.service;

import com.timelapse.backend.entity.MonitoringType;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

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
