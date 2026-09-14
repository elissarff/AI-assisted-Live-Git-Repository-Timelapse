package com.timelapse.backend.service;

import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.entity.GitHubInstallationEntity;
import com.timelapse.backend.entity.MonitoringType;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.github.GitHubTokenService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepositorySyncServiceTest {
    @Test
    void unchangedShaDoesNotProcessCommits() throws Exception {
        Fixture f = fixture(MonitoringType.POLLING, "abc");
        when(f.git.repositoryExists(any())).thenReturn(true);
        when(f.git.getRemoteBranchHead(any(), eq("main"))).thenReturn("abc");

        var result = f.service.sync(1L);

        assertFalse(result.changed());
        assertEquals(0, result.commitsProcessed());
        verify(f.git, never()).getCommitsBetween(any(), any(), any());
        verify(f.token, never()).getInstallationToken(anyLong());
    }

    @Test
    void updatesLastProcessedShaWhenNewCommitsExist() throws Exception {
        Fixture f = fixture(MonitoringType.POLLING, "old");
        when(f.git.repositoryExists(any())).thenReturn(true);
        when(f.git.getRemoteBranchHead(any(), eq("main"))).thenReturn("new");
        when(f.git.commitExists(any(), eq("old"))).thenReturn(true);
        when(f.git.isAncestor(any(), eq("old"), eq("new"))).thenReturn(true);
        when(f.git.getCommitsBetween(any(), eq("old"), eq("new")))
                .thenReturn(List.of(new CommitDto("new", "message", "author", Instant.now())));

        var result = f.service.sync(1L);

        assertTrue(result.changed());
        assertEquals("new", f.entity.getLastProcessedSha());
        assertEquals(1, result.commitsProcessed());
    }

    @Test
    void githubAppRepositoryRequestsInstallationToken() throws Exception {
        Fixture f = fixture(MonitoringType.GITHUB_APP, "same");
        GitHubInstallationEntity installation = new GitHubInstallationEntity();
        installation.setGithubInstallationId(987L);
        f.entity.setInstallation(installation);
        when(f.token.getInstallationToken(987L)).thenReturn("temporary-token");
        when(f.git.repositoryExists(any())).thenReturn(true);
        when(f.git.getRemoteBranchHead(any(), eq("main"))).thenReturn("same");

        f.service.sync(1L);

        verify(f.token).getInstallationToken(987L);
        verify(f.git).fetch(any(), notNull());
    }

    @Test
    void publicRepositoryDoesNotRequestGitHubCredentials() throws Exception {
        Fixture f = fixture(MonitoringType.POLLING, "same");
        when(f.git.repositoryExists(any())).thenReturn(true);
        when(f.git.getRemoteBranchHead(any(), eq("main"))).thenReturn("same");

        f.service.sync(1L);

        verify(f.token, never()).getInstallationToken(anyLong());
        verify(f.git).fetch(any(), isNull());
    }

    private Fixture fixture(MonitoringType type, String sha) throws Exception {
        RepositoryJpaRepository repository = mock(RepositoryJpaRepository.class);
        GitService git = mock(GitService.class);
        GitHubTokenService token = mock(GitHubTokenService.class);
        RepositoryEntity entity = new RepositoryEntity();
        var idField = RepositoryEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, 1L);
        entity.setRepoKey(UUID.randomUUID());
        entity.setRemoteUrl("https://github.com/example/repo.git");
        entity.setDefaultBranch("main");
        Path dir = Files.createTempDirectory("repo-sync-test").resolve("repo.git");
        entity.setLocalGitDirectory(dir.toString());
        entity.setMonitoringType(type);
        entity.setLastProcessedSha(sha);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new Fixture(new RepositorySyncService(repository, git, token), repository, git, token, entity);
    }

    private record Fixture(RepositorySyncService service, RepositoryJpaRepository repository,
                           GitService git, GitHubTokenService token, RepositoryEntity entity) {}
}
