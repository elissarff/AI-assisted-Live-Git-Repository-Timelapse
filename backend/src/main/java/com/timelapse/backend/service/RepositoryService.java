package com.timelapse.backend.service;

import com.timelapse.backend.config.GitProperties;
import com.timelapse.backend.dto.CloneRepositoryRequest;
import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.dto.RegisteredRepositoryDto;
import com.timelapse.backend.dto.RepositoryInfoDto;
import com.timelapse.backend.dto.SyncResultDto;
import com.timelapse.backend.entity.GitHubInstallationEntity;
import com.timelapse.backend.entity.MonitoringType;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.entity.RepositoryVisibility;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.github.GitHubInstallationService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class RepositoryService {
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final GitService gitService;
    private final GitProperties properties;
    private final RepositorySyncService repositorySyncService;
    private final GitHubInstallationService gitHubInstallationService;

    public RepositoryService(GitService gitService,
                             GitProperties properties,
                             RepositoryJpaRepository repositoryJpaRepository,
                             RepositorySyncService repositorySyncService,
                             GitHubInstallationService gitHubInstallationService) {
        this.gitService = gitService;
        this.properties = properties;
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.repositorySyncService = repositorySyncService;
        this.gitHubInstallationService = gitHubInstallationService;
    }

    public RegisteredRepositoryDto register(CloneRepositoryRequest request) throws Exception {
        String remoteUrl = normalizeAndValidateRemoteUrl(request.remoteUrl());
        if (repositoryJpaRepository.existsByRemoteUrl(remoteUrl)) {
            throw new IllegalArgumentException("Repository already registered: " + remoteUrl);
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
        String headSha = gitService.getRemoteBranchHead(gitDirectory, repository.getDefaultBranch());
        List<CommitDto> recentCommits = gitService.getRecentCommits(gitDirectory, repository.getDefaultBranch(), 10);
        int commitCount = gitService.countCommits(gitDirectory, repository.getDefaultBranch());
        return new RepositoryInfoDto(repository.getRepoKey().toString(), repository.getName(),
                repository.getRemoteUrl(), repository.getDefaultBranch(), headSha, commitCount, recentCommits);
    }

    public SyncResultDto sync(String id) throws Exception {
        return repositorySyncService.sync(id);
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
