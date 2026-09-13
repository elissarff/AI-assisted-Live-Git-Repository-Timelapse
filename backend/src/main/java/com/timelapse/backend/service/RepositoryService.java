package com.timelapse.backend.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.springframework.stereotype.Service;

import com.timelapse.backend.config.GitProperties;
import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.dto.RegisteredRepositoryDto;
import com.timelapse.backend.dto.RepositoryInfoDto;
import com.timelapse.backend.dto.SyncResultDto;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;


@Service
public class RepositoryService {
    private final RepositoryJpaRepository JPArepository;
    private final GitService gitService;
    private final GitProperties properties;


    public RepositoryService(
        GitService gitService,
        GitProperties properties,
        RepositoryJpaRepository JPArepository
    ) {
        this.gitService = gitService;
        this.properties = properties;
        this.JPArepository = JPArepository;
    }

    public RegisteredRepositoryDto register(
        String remoteUrl
    ) throws Exception {

        if (JPArepository
                .existsByRemoteUrl(remoteUrl)) {
                throw new IllegalArgumentException(
                        "Repository already registered: "
                                + remoteUrl
                );
        }

        UUID repoKey =
                UUID.randomUUID();

        String name =
                extractRepositoryName(
                        remoteUrl
                );

        Path repositoriesDirectory =
                Path.of(
                        properties
                                .getRepositoriesDirectory()
                );

        Files.createDirectories(
                repositoriesDirectory
        );

        Path gitDirectory =
                repositoriesDirectory.resolve(
                        repoKey + ".git"
                );

        gitService.cloneBare(
                remoteUrl,
                gitDirectory
        );

        String branch;

        try (Repository repository =
                        gitService.openRepository(
                                gitDirectory
                        )) {

                branch =
                        repository.getBranch();
        }

        String headSha =
                gitService.getRemoteBranchHead(
                        gitDirectory,
                        branch
                );

        RepositoryEntity entity =
                new RepositoryEntity();

        entity.setRepoKey(repoKey);
        entity.setName(name);
        entity.setRemoteUrl(remoteUrl);
        entity.setDefaultBranch(branch);
        entity.setLocalGitDirectory(
                gitDirectory.toString()
        );
        entity.setLastProcessedSha(
                headSha
        );

        entity =
                JPArepository.save(
                        entity
                );

        return new RegisteredRepositoryDto(
                entity.getRepoKey().toString(),
                entity.getName(),
                entity.getRemoteUrl(),
                entity.getDefaultBranch(),
                entity.getLastProcessedSha()
        );
    }

    
    public RepositoryInfoDto inspect(
        String id
        ) throws Exception {

        RepositoryEntity repository =
                requireRepository(id);

        Path gitDirectory =
                Path.of(
                        repository
                                .getLocalGitDirectory()
                );

        String headSha =
                gitService.getRemoteBranchHead(
                        gitDirectory,
                        repository.getDefaultBranch()
                );

        List<CommitDto> recentCommits =
                gitService.getRecentCommits(
                        gitDirectory,
                        repository.getDefaultBranch(),
                        10
                );

        int commitCount =
                gitService.countCommits(
                        gitDirectory,
                        repository.getDefaultBranch()
                );

        return new RepositoryInfoDto(
                repository
                        .getRepoKey()
                        .toString(),
                repository.getName(),
                repository.getRemoteUrl(),
                repository.getDefaultBranch(),
                headSha,
                commitCount,
                recentCommits
        );
    }

    public SyncResultDto sync(
            String id
    ) throws Exception {

        RepositoryEntity repository =
                requireRepository(id);

        Path gitDirectory =
                Path.of(
                        repository
                                .getLocalGitDirectory()
                );

        String previousSha =
                repository
                        .getLastProcessedSha();

        gitService.fetch(
                gitDirectory
        );

        String currentSha =
                gitService.getRemoteBranchHead(
                        gitDirectory,
                        repository.getDefaultBranch()
                );

        List<CommitDto> newCommits =
                gitService.getCommitsBetween(
                        gitDirectory,
                        previousSha,
                        currentSha
                );

        repository.setLastProcessedSha(
                currentSha
        );

        JPArepository.save(
                repository
        );

        return new SyncResultDto(
                repository
                        .getRepoKey()
                        .toString(),
                previousSha,
                currentSha,
                newCommits.size(),
                newCommits
        );
    }

    private RepositoryEntity requireRepository(
        String id
    ) throws RepositoryNotFoundException {

        UUID repoKey;

        try {
                repoKey = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
                throw new RepositoryNotFoundException(
                        "Invalid repository id: " + id
                );
        }

        return JPArepository
                .findByRepoKey(repoKey)
                .orElseThrow(() ->
                        new RepositoryNotFoundException(
                                "Repository not found: "
                                        + id
                        )
                );
    }

    public RepositoryEntity findByRemoteUrl(
        String remoteUrl
    ) {

        return JPArepository
                .findByRemoteUrl(remoteUrl)
                .orElse(null);
    }

    private String extractRepositoryName(
            String remoteUrl
    ) {

        String cleaned =
                remoteUrl.endsWith(".git")
                        ? remoteUrl.substring(
                                0,
                                remoteUrl.length() - 4
                        )
                        : remoteUrl;

        int slash =
                cleaned.lastIndexOf('/');

        if (slash >= 0) {
            return cleaned.substring(
                    slash + 1
            );
        }

        return cleaned;
    }
}