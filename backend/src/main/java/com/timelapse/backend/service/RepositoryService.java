package com.timelapse.backend.service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.timelapse.backend.config.GitProperties;
import com.timelapse.backend.dto.CloneRepositoryRequest;
import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.dto.CommitDetailDto;
import com.timelapse.backend.dto.FileContentDto;
import com.timelapse.backend.dto.RegisteredRepositoryDto;
import com.timelapse.backend.dto.RepositoryInfoDto;
import com.timelapse.backend.dto.RepositoryTreeDto;
import com.timelapse.backend.dto.SyncResultDto;
import com.timelapse.backend.entity.GitHubInstallationEntity;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.git.GitMiningService;
import com.timelapse.backend.service.github.GitHubInstallationService;
import com.timelapse.backend.types.MonitoringType;
import com.timelapse.backend.types.RepositoryVisibility;

@Service
public class RepositoryService {
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final GitMiningService gitMiningService;
    private final GitProperties properties;
    private final RepositorySyncService repositorySyncService;
    private final GitHubInstallationService gitHubInstallationService;

    public RepositoryService(GitMiningService gitMiningService,
                             GitProperties properties,
                             RepositoryJpaRepository repositoryJpaRepository,
                             RepositorySyncService repositorySyncService,
                             GitHubInstallationService gitHubInstallationService) {
        this.gitMiningService = gitMiningService;
        this.properties = properties;
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.repositorySyncService = repositorySyncService;
        this.gitHubInstallationService = gitHubInstallationService;
    }

    public RegisteredRepositoryDto register(CloneRepositoryRequest request) throws Exception {
        String remoteUrl = normalizeAndValidateRemoteUrl(request.remoteUrl());
        Optional<RepositoryEntity> existing =
        repositoryJpaRepository.findByRemoteUrl(request.remoteUrl());

        // TEMPORARY
        if (existing.isPresent()) {
            return toRegisteredDto(existing.get(), existing.get().getLastProcessedSha());
        }

        MonitoringType monitoringType = request.monitoringType() == null ? MonitoringType.POLLING : request.monitoringType();
        RepositoryVisibility visibility = request.visibility() == null
                ? (monitoringType == MonitoringType.GITHUB_APP ? RepositoryVisibility.PRIVATE : RepositoryVisibility.PUBLIC)
                : request.visibility();

        if (visibility == RepositoryVisibility.PRIVATE && monitoringType != MonitoringType.GITHUB_APP) {
            throw new IllegalArgumentException("Private repositories must use GITHUB_APP monitoring");
        }
        if (monitoringType == MonitoringType.GITHUB_APP && request.installationId() == null) {
            throw new IllegalArgumentException("installationId is required for GITHUB_APP repositories");
        }

        UUID repoKey = UUID.randomUUID();
        Path baseDirectory = Path.of(properties.getRepositoriesDirectory());
        Files.createDirectories(baseDirectory);

        RepositoryEntity entity = new RepositoryEntity();
        entity.setRepoKey(repoKey);
        entity.setName(extractRepositoryName(remoteUrl));
        entity.setFullName(request.fullName() == null || request.fullName().isBlank()
                ? extractGitHubFullName(remoteUrl) : request.fullName());
        entity.setRemoteUrl(remoteUrl);
        entity.setDefaultBranch(request.defaultBranch());
        entity.setLocalGitDirectory(baseDirectory.resolve(repoKey + ".git").toString());
        entity.setProviderRepositoryId(request.providerRepositoryId());
        entity.setMonitoringType(monitoringType);
        entity.setVisibility(visibility);

        if (monitoringType == MonitoringType.GITHUB_APP) {
            GitHubInstallationEntity installation = gitHubInstallationService.require(request.installationId());
            entity.setInstallation(installation);
        }

        entity = repositoryJpaRepository.save(entity);
        SyncResultDto initialSync;
        try {
            initialSync = repositorySyncService.sync(entity.getId());
        } catch (Exception ex) {
            repositoryJpaRepository.deleteById(entity.getId());
            try { deleteRecursively(Path.of(entity.getLocalGitDirectory())); } catch (Exception ignored) { }
            throw ex;
        }

        RepositoryEntity saved = requireRepository(repoKey.toString());
        return toRegisteredDto(saved, initialSync.currentSha());
    }

    public RepositoryInfoDto inspect(String id) throws Exception {
        RepositoryEntity repository = requireRepository(id);
        Path gitDirectory = Path.of(repository.getLocalGitDirectory());
        String headSha = gitMiningService.getRemoteBranchHead(gitDirectory, repository.getDefaultBranch());
        List<CommitDto> recentCommits = gitMiningService.getRecentCommits(gitDirectory, repository.getDefaultBranch(), 10);
        int commitCount = gitMiningService.countCommits(gitDirectory, repository.getDefaultBranch());
        return new RepositoryInfoDto(repository.getRepoKey().toString(), repository.getName(),
                repository.getRemoteUrl(), repository.getDefaultBranch(), headSha, commitCount, recentCommits);
    }

    public SyncResultDto sync(String id) throws Exception {
        return repositorySyncService.sync(id);
    }

    /**
     * Returns the complete tracked/default-branch history in chronological
     * order. Git remains authoritative; commit history is not duplicated in
     * PostgreSQL.
     */
    public List<CommitDto> getTimeline(String id) throws Exception {
        RepositoryEntity repository = requireRepository(id);
        Path gitDirectory = Path.of(repository.getLocalGitDirectory());
        String headSha = gitMiningService.getRemoteBranchHead(gitDirectory, repository.getDefaultBranch());
        if (headSha == null) return List.of();
        return gitMiningService.getAllCommits(gitDirectory, headSha);
    }

    /** Returns the Git-derived details required by the commit detail view. */
    public CommitDetailDto getCommitDetail(String id, String sha) throws Exception {
        RepositoryEntity repository = requireRepository(id);
        validateSha(sha);
        return gitMiningService.getCommitDetail(Path.of(repository.getLocalGitDirectory()), sha);
    }

    /** Returns the complete file/folder snapshot at the requested commit. */
    public RepositoryTreeDto getRepositoryTree(String id, String sha) throws Exception {
        RepositoryEntity repository = requireRepository(id);
        validateSha(sha);
        return gitMiningService.getRepositoryTree(
                Path.of(repository.getLocalGitDirectory()), sha);
    }

    /** Returns a file exactly as it existed at the requested commit. */
    public FileContentDto getFileContent(String id, String sha, String filePath) throws Exception {
        RepositoryEntity repository = requireRepository(id);
        validateSha(sha);
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        if (filePath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid file path");
        }
        return gitMiningService.getFileContent(
                Path.of(repository.getLocalGitDirectory()), sha, filePath);
    }

    private void validateSha(String sha) {
        if (sha == null || sha.isBlank()) {
            throw new IllegalArgumentException("Commit SHA is required");
        }
        // Accept abbreviated Git object ids while rejecting revision expressions
        // such as HEAD~1. The timelapse should address concrete commits only.
        if (!sha.matches("[0-9a-fA-F]{4,64}")) {
            throw new IllegalArgumentException("Invalid commit SHA: " + sha);
        }
    }

    public RepositoryEntity requireRepository(String id) {
        try {
            UUID repoKey = UUID.fromString(id);
            return repositoryJpaRepository.findByRepoKey(repoKey)
                    .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + id));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Repository not found")) throw ex;
            throw new IllegalArgumentException("Invalid repository id: " + id, ex);
        }
    }

    public RepositoryEntity findByProviderRepositoryId(Long providerRepositoryId) {
        if (providerRepositoryId == null) return null;
        return repositoryJpaRepository.findByProviderRepositoryId(providerRepositoryId).orElse(null);
    }

    public RepositoryEntity findByFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) return null;
        return repositoryJpaRepository.findByFullNameIgnoreCase(fullName).orElse(null);
    }

    public RepositoryEntity save(RepositoryEntity entity) {
        return repositoryJpaRepository.save(entity);
    }

    private RegisteredRepositoryDto toRegisteredDto(RepositoryEntity entity, String headSha) {
        Long installationId = entity.getInstallation() == null ? null : entity.getInstallation().getGithubInstallationId();
        return new RegisteredRepositoryDto(entity.getRepoKey().toString(), entity.getName(), entity.getFullName(),
                entity.getRemoteUrl(), entity.getDefaultBranch(), headSha, entity.getMonitoringType(),
                entity.getVisibility(), entity.getProviderRepositoryId(), installationId);
    }

    private String normalizeAndValidateRemoteUrl(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) throw new IllegalArgumentException("remoteUrl is required");
        URI uri;
        try { uri = URI.create(remoteUrl.trim()); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Unsupported remote URL", ex); }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Only credential-free HTTP(S) Git remote URLs are supported");
        }
        return remoteUrl.trim();
    }

    private String extractRepositoryName(String remoteUrl) {
        String cleaned = remoteUrl.endsWith(".git") ? remoteUrl.substring(0, remoteUrl.length() - 4) : remoteUrl;
        int slash = cleaned.lastIndexOf('/');
        return slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
    }

    private String extractGitHubFullName(String remoteUrl) {
        URI uri = URI.create(remoteUrl);
        if (!"github.com".equalsIgnoreCase(uri.getHost())) return null;
        String path = uri.getPath();
        if (path == null) return null;
        path = path.replaceAll("^/+|/+$", "");
        if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
        return path.split("/").length == 2 ? path : null;
    }

    private void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) { }
            });
        }
    }
}
