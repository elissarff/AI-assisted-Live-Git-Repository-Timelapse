package com.timelapse.backend.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.lib.Repository;
import org.springframework.stereotype.Service;

import com.timelapse.backend.config.GitProperties;
import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.dto.RegisteredRepositoryDto;
import com.timelapse.backend.dto.RepositoryInfoDto;
import com.timelapse.backend.dto.SyncResultDto;
import com.timelapse.backend.model.RegisteredRepository;

@Service
public class RepositoryService {

    private final GitService gitService;
    private final GitProperties properties;

    /*
     * Temporary replacement for database.
     */
    private final Map<String, RegisteredRepository>
            repositories =
            new ConcurrentHashMap<>();

    public RepositoryService(
            GitService gitService,
            GitProperties properties
    ) {
        this.gitService = gitService;
        this.properties = properties;
    }

    public RegisteredRepositoryDto register(
            String remoteUrl
    ) throws Exception {

        String id =
                UUID.randomUUID().toString();

        String name =
                extractRepositoryName(remoteUrl);

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
                        id + ".git"
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

        RegisteredRepository registered =
                new RegisteredRepository(
                        id,
                        name,
                        remoteUrl,
                        gitDirectory,
                        branch,
                        headSha
                );

        repositories.put(
                id,
                registered
        );

        return new RegisteredRepositoryDto(
                id,
                name,
                remoteUrl,
                branch,
                headSha
        );
    }

    public RepositoryInfoDto inspect(
            String id
    ) throws Exception {

        RegisteredRepository repository =
                requireRepository(id);

        String headSha =
                gitService.getRemoteBranchHead(
                        repository.getGitDirectory(),
                        repository.getDefaultBranch()
                );

        List<CommitDto> recentCommits =
                gitService.getRecentCommits(
                        repository.getGitDirectory(),
                        repository.getDefaultBranch(),
                        10
                );

        int commitCount =
                gitService.countCommits(
                        repository.getGitDirectory(),
                        repository.getDefaultBranch()
                );

        return new RepositoryInfoDto(
                repository.getId(),
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

        RegisteredRepository repository =
                requireRepository(id);

        String previousSha =
                repository.getLastProcessedSha();

        gitService.fetch(
                repository.getGitDirectory()
        );

        String currentSha =
                gitService.getRemoteBranchHead(
                        repository.getGitDirectory(),
                        repository.getDefaultBranch()
                );

        List<CommitDto> newCommits =
                gitService.getCommitsBetween(
                        repository.getGitDirectory(),
                        previousSha,
                        currentSha
                );

        /*
         * Eventually:
         *
         * commitAnalysisService.process(newCommits)
         */

        repository.setLastProcessedSha(
                currentSha
        );

        return new SyncResultDto(
                id,
                previousSha,
                currentSha,
                newCommits.size(),
                newCommits
        );
    }

    public RegisteredRepository findByRemoteUrl(
            String remoteUrl
    ) {

        return repositories
                .values()
                .stream()
                .filter(repo ->
                        repo.getRemoteUrl()
                                .equals(remoteUrl)
                )
                .findFirst()
                .orElse(null);
    }

    private RegisteredRepository requireRepository(
            String id
    ) {

        RegisteredRepository repository =
                repositories.get(id);

        if (repository == null) {
            throw new IllegalArgumentException(
                    "Repository not found: " + id
            );
        }

        return repository;
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