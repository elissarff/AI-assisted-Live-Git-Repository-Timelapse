package com.timelapse.backend.service.refactoring;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RefactoringRepositorySettingsJpaRepository;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.git.GitRepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefactoringAnalysisServiceTest {
    @Test
    void manualWorkerStartsWhileHistoryDetectorIsBusy() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var repositories = mock(RepositoryJpaRepository.class);
        var entity = mock(RepositoryEntity.class);
        when(entity.getLocalGitDirectory()).thenReturn("/tmp/test.git");
        when(repositories.findById(1L)).thenReturn(Optional.of(entity));
        when(repositories.findById(2L)).thenReturn(Optional.of(entity));
        when(jdbc.queryForList(anyString(), eq(false), any(UUID.class))).thenReturn(
            List.of(Map.of("id", 1L, "repository_id", 1L, "commit_sha", "history")),
            List.of(Map.of("id",3L,"repository_id",2L,"commit_sha","history")), List.of());
        when(jdbc.queryForList(anyString(), eq(true), any(UUID.class))).thenReturn(
            List.of(Map.of("id", 2L, "repository_id", 1L, "commit_sha", "manual")), List.of());
        var historyStarted = new java.util.concurrent.CountDownLatch(2);
        var manualStarted = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var detector = mock(RefactoringDetector.class);
        when(detector.detect(any(), eq("history"))).thenAnswer(i -> {
            historyStarted.countDown();
            release.await();
            return new RefactoringResult(null, List.of());
        });
        when(detector.detect(any(), eq("manual"))).thenAnswer(i -> {
            manualStarted.countDown();
            release.await();
            return new RefactoringResult(null, List.of());
        });
        var service = new RefactoringAnalysisService(repositories,
            mock(RefactoringRepositorySettingsJpaRepository.class), new GitRepositoryService(),
            jdbc, new ObjectMapper(), mock(PlatformTransactionManager.class), detector, 2);
        try {
            service.dispatch();
            assertTrue(historyStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(manualStarted.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "Manual detection must start before history detection finishes");
            service.dispatch();
            verify(detector, times(2)).detect(any(), eq("history"));
            verify(detector, times(1)).detect(any(), eq("manual"));
        } finally { release.countDown(); service.stopWorkers(); }
    }

    @Test
    void timedOutCommitFailsAndNextQueuedCommitStillCompletesWithoutAutomaticFailureRetries() throws Exception {
        String sha = "a".repeat(40);
        var entity = mock(RepositoryEntity.class);
        when(entity.getLocalGitDirectory()).thenReturn("/tmp/test-refactoring.git");
        var repositories = mock(RepositoryJpaRepository.class);
        when(repositories.findById(1L)).thenReturn(Optional.of(entity));
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(false), any(UUID.class))).thenAnswer(invocation -> {
            assertFalse(invocation.<String>getArgument(0).contains("j.status = 'FAILED'"),
                "Failed analyses must require an explicit retry");
            return List.of(Map.of("id", 1L, "repository_id", 1L, "commit_sha", sha));
        }).thenReturn(List.of(Map.of("id", 2L, "repository_id", 1L, "commit_sha", sha, "timeout_seconds", 300)), List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var detector = mock(RefactoringDetector.class);
        when(detector.detect(any(), eq(sha))).thenThrow(new TimeoutException("Analysis exceeded 60 seconds"));
        when(detector.detect(any(), eq(sha), eq(300))).thenReturn(new RefactoringResult("b".repeat(40), List.of()));
        var service = new RefactoringAnalysisService(repositories,
            mock(RefactoringRepositorySettingsJpaRepository.class), new GitRepositoryService(),
            jdbc, new ObjectMapper(), manager, detector, 1);
        try {
            assertEquals(true, ReflectionTestUtils.invokeMethod(service, "processNext", false));
            verify(jdbc).update(contains("SET status = ?"), eq("FAILED"), startsWith("TIMEOUT:"), eq(1L), any(UUID.class));
            verify(jdbc, never()).update(startsWith("DELETE FROM detected_refactorings"), eq(1L));
            assertEquals(true, ReflectionTestUtils.invokeMethod(service, "processNext", false));
            verify(jdbc).update(contains("SET status = ?"), eq("COMPLETED"), isNull(), eq(2L), any(UUID.class));
            verify(detector).detect(any(), eq(sha), eq(300));
            assertEquals(false, ReflectionTestUtils.invokeMethod(service, "processNext", false));
        } finally { service.stopWorkers(); }
    }
}
