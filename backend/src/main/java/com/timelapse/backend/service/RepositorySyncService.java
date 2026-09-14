package com.timelapse.backend.service;

import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.dto.SyncResultDto;
import com.timelapse.backend.entity.MonitoringType;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.github.GitHubTokenService;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class RepositorySyncService {
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final GitService gitService;
    private final GitHubTokenService gitHubTokenService;
    private final ConcurrentHashMap<Long, ReentrantLock> repositoryLocks = new ConcurrentHashMap<>();

    public RepositorySyncService(
            RepositoryJpaRepository repositoryJpaRepository,
            GitService gitService,
            GitHubTokenService gitHubTokenService
    ) {
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.gitService = gitService;
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
        CredentialsProvider credentials = credentialsFor(repository);
        Path gitDirectory = Path.of(repository.getLocalGitDirectory());
        Files.createDirectories(gitDirectory.getParent());

        if (!gitService.repositoryExists(gitDirectory)) {
            gitService.cloneBare(repository.getRemoteUrl(), gitDirectory, credentials);
        } else {
            gitService.fetch(gitDirectory, credentials);
        }

        if (repository.getDefaultBranch() == null || repository.getDefaultBranch().isBlank()) {
            repository.setDefaultBranch(gitService.detectDefaultBranch(gitDirectory));
        }

        String previousSha = repository.getLastProcessedSha();
        String currentSha = gitService.getRemoteBranchHead(gitDirectory, repository.getDefaultBranch());
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

        List<CommitDto> commits;
        if (previousSha == null) {
            commits = gitService.getAllCommits(gitDirectory, currentSha);
        } else if (!gitService.commitExists(gitDirectory, previousSha)
                || !gitService.isAncestor(gitDirectory, previousSha, currentSha)) {
            // Force-push/stale SHA: safely rebuild the reachable history instead of failing forever.
            commits = gitService.getAllCommits(gitDirectory, currentSha);
        } else {
            commits = gitService.getCommitsBetween(gitDirectory, previousSha, currentSha);
        }

        // This is the shared hook for commit persistence/AI processing when those services are added.
        repository.setLastProcessedSha(currentSha);
        repository.setLastCheckedAt(syncedAt);
        repositoryJpaRepository.save(repository);

        return new SyncResultDto(repository.getRepoKey().toString(), previousSha, currentSha,
                true, commits.size(), commits.size(), commits, syncedAt);
    }

    private CredentialsProvider credentialsFor(RepositoryEntity repository) throws Exception {
        if (repository.getMonitoringType() != MonitoringType.GITHUB_APP) {
            return null;
        }
        if (repository.getInstallation() == null || repository.getInstallation().getGithubInstallationId() == null) {
            throw new IllegalStateException("GitHub App repository is missing an installation association");
        }
        String token = gitHubTokenService.getInstallationToken(repository.getInstallation().getGithubInstallationId());
        return new UsernamePasswordCredentialsProvider("x-access-token", token);
    }
}
