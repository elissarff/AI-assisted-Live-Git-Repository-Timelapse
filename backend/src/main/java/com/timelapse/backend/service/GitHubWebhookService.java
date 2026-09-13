package com.timelapse.backend.service;

import org.springframework.stereotype.Service;

import com.timelapse.backend.entity.RepositoryEntity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GitHubWebhookService {

    private final RepositoryService repositoryService;
    private final ObjectMapper objectMapper;

    public GitHubWebhookService(
            RepositoryService repositoryService,
            ObjectMapper objectMapper
    ) {
        this.repositoryService = repositoryService;
        this.objectMapper = objectMapper;
    }

    public void handlePush(byte[] payload) throws Exception {

        JsonNode root = objectMapper.readTree(payload);

        JsonNode repositoryNode =
                root.get("repository");

        if (repositoryNode == null) {
            System.out.println(
                    "GitHub webhook did not contain repository information"
            );
            return;
        }

        String cloneUrl =
                repositoryNode
                        .path("clone_url")
                        .asText();

        if (cloneUrl == null || cloneUrl.isBlank()) {
            System.out.println(
                    "GitHub webhook did not contain clone_url"
            );
            return;
        }

        System.out.println(
                "Webhook received for: " + cloneUrl
        );

        RepositoryEntity repository =
                repositoryService.findByRemoteUrl(cloneUrl);

        if (repository == null) {
            System.out.println(
                    "Repository is not registered: "
                            + cloneUrl
            );
            return;
        }

        var result =
                repositoryService.sync(
                        repository.getRepoKey().toString()
                );

        System.out.println(
                "Webhook sync complete."
        );

        System.out.println(
                "Previous SHA: "
                        + result.previousSha()
        );

        System.out.println(
                "Current SHA: "
                        + result.currentSha()
        );

        System.out.println(
                "New commits: "
                        + result.newCommitCount()
        );
    }
}