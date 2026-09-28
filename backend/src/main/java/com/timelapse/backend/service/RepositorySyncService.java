package com.timelapse.backend.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.dto.SyncResultDto;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.git.GitMiningService;
import com.timelapse.backend.service.git.GitRepositoryService;
import com.timelapse.backend.service.github.GitHubTokenService;
import com.timelapse.backend.types.MonitoringType;

@Service
public class RepositorySyncService {
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final GitRepositoryService gitRepositoryService;
    private final GitMiningService gitMiningService;
    private final GitHubTokenService gitHubTokenService;
    private final ConcurrentHashMap<Long, ReentrantLock> repositoryLocks = new ConcurrentHashMap<>();

    public RepositorySyncService(
            RepositoryJpaRepository repositoryJpaRepository,
            GitRepositoryService gitRepositoryService,
            GitMiningService gitMiningService,
            GitHubTokenService gitHubTokenService
    ) {
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.gitRepositoryService = gitRepositoryService;
        this.gitMiningService = gitMiningService;
        this.gitHubTokenService = gitHubTokenService;
    }

    public SyncResultDto sync(String repoKey) throws Exception {
        UUID key;
        try {
            key = UUID.fromString(repoKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid repository id: " + repoKey, ex);
        }
        RepositoryEntity repository = repositoryJpaRepository.findByRepoKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoKey));
        return sync(repository.getId());
    }

    public SyncResultDto sync(Long repositoryId) throws Exception {
        ReentrantLock lock = repositoryLocks.computeIfAbsent(repositoryId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            RepositoryEntity repository = repositoryJpaRepository.findById(repositoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));
            return doSync(repository);
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) repositoryLocks.remove(repositoryId, lock);
        }
    }

    @Transactional
    protected SyncResultDto doSync(RepositoryEntity repository) throws Exception {
        String token = tokenFor(repository);
        Path gitDirectory = Path.of(repository.getLocalGitDirectory());
        Files.createDirectories(gitDirectory.getParent());

        if (!gitRepositoryService.repositoryExists(gitDirectory)) {
            gitRepositoryService.cloneBare(repository.getRemoteUrl(), gitDirectory, "x-access-token", token);
        } else {
            gitRepositoryService.fetch(gitDirectory, "x-access-token", token);
        }

        if (repository.getDefaultBranch() == null || repository.getDefaultBranch().isBlank()) {
            repository.setDefaultBranch(gitRepositoryService.detectDefaultBranch(gitDirectory));
        }

        String previousSha = repository.getLastProcessedSha();
        String currentSha = gitRepositoryService.getRemoteBranchHead(gitDirectory, repository.getDefaultBranch());
        if (currentSha == null) {
            throw new IllegalStateException("Unable to resolve branch '" + repository.getDefaultBranch() + "' for repository " + repository.getRepoKey());
        }

        OffsetDateTime syncedAt = OffsetDateTime.now();
        if (currentSha.equals(previousSha)) {
            repository.setLastCheckedAt(syncedAt);
            repositoryJpaRepository.save(repository);
            return new SyncResultDto(repository.getRepoKey().toString(), previousSha, currentSha,
                    false, 0, 0, List.of(), syncedAt);
        }

        // Mining owns the repository lifetime for the history check and traversal, so this sync
        // operation does not repeatedly reopen the same repository or handle JGit objects here.
        List<CommitDto> commits = gitMiningService.getCommitsForSync(gitDirectory, previousSha, currentSha);

        // This is the shared hook for commit persistence/AI processing when those services are added.
        repository.setLastProcessedSha(currentSha);
        repository.setLastCheckedAt(syncedAt);
        repositoryJpaRepository.save(repository);

        return new SyncResultDto(repository.getRepoKey().toString(), previousSha, currentSha,
                true, commits.size(), commits.size(), commits, syncedAt);
    }

    private String tokenFor(RepositoryEntity repository) throws Exception {
        if (repository.getMonitoringType() != MonitoringType.GITHUB_APP) {
            return null;
        }
        if (repository.getInstallation() == null || repository.getInstallation().getGithubInstallationId() == null) {
            throw new IllegalStateException("GitHub App repository is missing an installation association");
        }
        String token = gitHubTokenService.getInstallationToken(repository.getInstallation().getGithubInstallationId());
        return token;
    }
}
