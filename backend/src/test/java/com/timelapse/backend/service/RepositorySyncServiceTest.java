package com.timelapse.backend.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.entity.GitHubInstallationEntity;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.git.GitMiningService;
import com.timelapse.backend.service.git.GitRepositoryService;
import com.timelapse.backend.service.github.GitHubTokenService;
import com.timelapse.backend.types.MonitoringType;

class RepositorySyncServiceTest {
    @Test
    void unchangedShaDoesNotProcessCommits() throws Exception {
        Fixture f = fixture(MonitoringType.POLLING, "abc");
        when(f.git.repositoryExists(any())).thenReturn(true);
        when(f.git.getRemoteBranchHead(any(), eq("main"))).thenReturn("abc");

        var result = f.service.sync(1L);

        assertFalse(result.changed());
        assertEquals(0, result.commitsProcessed());
        verify(f.gitMining, never()).getCommitsBetween(any(), any(), any());
        verify(f.token, never()).getInstallationToken(anyLong());
    }

    @Test
    void updatesLastProcessedShaWhenNewCommitsExist() throws Exception {
        Fixture f = fixture(MonitoringType.POLLING, "old");
        when(f.git.repositoryExists(any())).thenReturn(true);
        when(f.git.getRemoteBranchHead(any(), eq("main"))).thenReturn("new");
        when(f.gitMining.commitExists(any(), eq("old"))).thenReturn(true);
        when(f.gitMining.isAncestor(any(), eq("old"), eq("new"))).thenReturn(true);
        when(f.gitMining.getCommitsBetween(any(), eq("old"), eq("new")))
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
        GitRepositoryService git = mock(GitRepositoryService.class);
        GitMiningService gitMining = mock(GitMiningService.class);
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
        return new Fixture(new RepositorySyncService(repository, git, gitMining, token), repository, git, gitMining, token, entity);
    }

    private record Fixture(RepositorySyncService service, RepositoryJpaRepository repository,
                           GitRepositoryService git, GitMiningService gitMining, GitHubTokenService token, RepositoryEntity entity) {}
}
