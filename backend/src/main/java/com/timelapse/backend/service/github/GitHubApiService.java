package com.timelapse.backend.service.github;

import com.timelapse.backend.dto.GitHubInstallationDto;
import com.timelapse.backend.dto.GitHubRepositoryDto;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class GitHubApiService {
    private final GitHubTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GitHubApiService(GitHubTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    public GitHubInstallationDto getInstallation(Long installationId) throws Exception {
        if (installationId == null) {
            throw new IllegalArgumentException("GitHub installation id is required");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/app/installations/" + installationId))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + tokenService.createAppJwt())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response, "GitHub installation lookup");

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode account = root.path("account");

        return new GitHubInstallationDto(
                root.path("id").asLong(),
                account.path("id").asLong(),
                account.path("login").asText()
        );
    }

    public List<GitHubRepositoryDto> getInstallationRepositories(Long installationId) throws Exception {
        if (installationId == null) {
            throw new IllegalArgumentException("GitHub installation id is required");
        }

        String token = tokenService.getInstallationToken(installationId);
        List<GitHubRepositoryDto> result = new ArrayList<>();

        int page = 1;
        while (true) {
            String url = "https://api.github.com/installation/repositories?per_page=100&page=" + page;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response, "GitHub installation repository lookup");

            JsonNode repositories = objectMapper.readTree(response.body()).path("repositories");
            if (!repositories.isArray() || repositories.isEmpty()) {
                break;
            }

            for (JsonNode repo : repositories) {
                result.add(new GitHubRepositoryDto(
                        repo.path("id").asLong(),
                        repo.path("full_name").asText(),
                        repo.path("name").asText(),
                        repo.path("private").asBoolean(),
                        repo.path("default_branch").asText(),
                        repo.path("clone_url").asText(),
                        repo.path("html_url").asText()
                ));
            }

            if (repositories.size() < 100) {
                break;
            }
            page++;
        }

        return result;
    }

    private void ensureSuccess(HttpResponse<String> response, String operation) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(operation + " failed with HTTP " + response.statusCode());
        }
    }
}
