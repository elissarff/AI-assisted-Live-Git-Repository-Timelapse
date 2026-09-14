package com.timelapse.backend.service.github;

import com.timelapse.backend.entity.MonitoringType;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.service.RepositoryService;
import com.timelapse.backend.service.RepositorySyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GitHubWebhookService {
    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);

    private final GitHubWebhookSignatureService signatureService;
    private final RepositoryService repositoryService;
    private final RepositorySyncService repositorySyncService;
    private final GitHubInstallationService installationService;
    private final ObjectMapper objectMapper;

    public GitHubWebhookService(GitHubWebhookSignatureService signatureService,
                                RepositoryService repositoryService,
                                RepositorySyncService repositorySyncService,
                                GitHubInstallationService installationService,
                                ObjectMapper objectMapper) {
        this.signatureService = signatureService;
        this.repositoryService = repositoryService;
        this.repositorySyncService = repositorySyncService;
        this.installationService = installationService;
        this.objectMapper = objectMapper;
    }

    public String handle(String event, String signature, String deliveryId, byte[] payload) throws Exception {
        if (!signatureService.isValid(payload, signature)) {
            throw new SecurityException("Invalid webhook signature");
        }
        if (event == null || event.isBlank()) return "Webhook received";
        if ("ping".equals(event)) return "Webhook connected";

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed webhook payload", ex);
        }

        if ("installation".equals(event)) {
            persistInstallation(root);
            return "Installation event processed";
        }
        if (!"push".equals(event)) return "Unsupported event ignored";

        handlePush(root, deliveryId);
        return "Webhook received";
    }

    private void handlePush(JsonNode root, String deliveryId) throws Exception {
        JsonNode repositoryNode = root.get("repository");
        if (repositoryNode == null || repositoryNode.isMissingNode()) {
            throw new IllegalArgumentException("Webhook payload did not contain repository information");
        }

        Long providerRepositoryId = repositoryNode.path("id").canConvertToLong()
                ? repositoryNode.path("id").asLong() : null;
        String fullName = repositoryNode.path("full_name").asText();
        String cloneUrl = repositoryNode.path("clone_url").asText();

        RepositoryEntity repository = repositoryService.findByProviderRepositoryId(providerRepositoryId);
        if (repository == null) {
            repository = repositoryService.findByFullName(fullName);
            if (repository != null && providerRepositoryId != null) {
                repository.setProviderRepositoryId(providerRepositoryId);
            }
        }
        if (repository == null) {
            log.info("Ignoring webhook for unregistered repository providerId={} fullName={} delivery={}",
                    providerRepositoryId, fullName, deliveryId);
            return;
        }

        if (fullName != null && !fullName.isBlank()) repository.setFullName(fullName);
        if (cloneUrl != null && !cloneUrl.isBlank()) repository.setRemoteUrl(cloneUrl);
        String defaultBranch = repositoryNode.path("default_branch").asText();
        if (defaultBranch != null && !defaultBranch.isBlank()) repository.setDefaultBranch(defaultBranch);

        JsonNode installationNode = root.get("installation");
        if (installationNode != null && installationNode.path("id").canConvertToLong()) {
            Long installationId = installationNode.path("id").asLong();
            JsonNode owner = repositoryNode.path("owner");
            Long accountId = owner.path("id").canConvertToLong() ? owner.path("id").asLong() : null;
            String login = owner.path("login").asText();
            repository.setInstallation(installationService.upsert(installationId, accountId, login));
            if (repository.getMonitoringType() == MonitoringType.WEBHOOK) {
                // Do not silently switch explicit WEBHOOK registrations, but preserve installation data.
            }
        }
        repositoryService.save(repository);
        repositorySyncService.sync(repository.getId());
    }

    private void persistInstallation(JsonNode root) {
        JsonNode installation = root.path("installation");
        if (!installation.path("id").canConvertToLong()) return;
        JsonNode account = installation.path("account");
        Long accountId = account.path("id").canConvertToLong() ? account.path("id").asLong() : null;
        String login = account.path("login").asText();
        installationService.upsert(installation.path("id").asLong(), accountId, login);
    }
}
