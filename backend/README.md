# Backend Developer Guide

## Purpose and architecture

The backend is a Java Spring Boot application (`BackendApplication.java`). It exposes repository/commit HTTP endpoints, reads authoritative Git history, computes metrics, manages repository registrations, and synchronizes repositories using polling or GitHub events. PostgreSQL persists application state through JPA and Flyway.

## Package navigation

| Location under `src/main/java/com/timelapse/backend/` | Responsibility |
|---|---|
| `controller/` | Request mapping, input/output HTTP boundary |
| `service/RepositoryService.java` | Repository application operations |
| `service/RepositorySyncService.java` | Shared repository synchronization workflow |
| `service/RepositoryPollingService.java` | Scheduled sync checks |
| `service/git/GitRepositoryService.java` | Git repository access |
| `service/git/GitCommitService.java` | Commit history and details |
| `service/git/GitDiffService.java` | Diff extraction |
| `service/git/GitFileService.java` | File content |
| `service/git/GitTreeService.java` | Repository trees |
| `service/git/GitMiningService.java` | Git mining utilities |
| `service/git/GitMetricsService.java` | Metrics aggregation |
| `service/git/metrics/` | Individual metrics: activity, churn, contributor, directory, hotspots, co-change |
| `service/github/` | GitHub API, installation, token, webhook and signature services |
| `dto/` | Request/response shapes (commit, file change, tree, metrics, repository, sync) |
| `entity/` and `repository/` | JPA entities and repositories |
| `types/` | Enums for monitoring, visibility, file changes, diff lines |
| `config/` | Git properties and web/CORS configuration |
| `exception/` | Application exception handling |

## HTTP entry points

- `HealthController`: `GET /health`.
- `RepositoryController`: `/api/repositories`, including `GET /{id}` and `POST /{id}/sync`; inspect the controller for other mappings without explicit paths.
- `CommitController`: `/api/repositories/{repositoryId}/commits`, including `/branches`, `/metrics`, `/metrics/{name}`, `/{sha}`, `/{sha}/tree`, and `/{sha}/file`.
- `GitHubConnectionController`: `/api/github/installations`, including `POST /{installationId}/connect` and `GET /{installationId}/repositories`.
- `GitHubWebhookController`: `/api/webhooks/github` for GitHub event intake.

Read the annotations and method signatures for exact verbs, query parameters, payloads, and response types; do not infer them from this route overview.

## How synchronization works

The proposal defines one shared sync pipeline: polling, a signed GitHub push webhook, and GitHub App authorization can all trigger `RepositorySyncService`. Git operations remain in the Git service layer. The tracked default branch and last-processed SHA form the checkpoint. A feature-branch push should not advance the default-branch checkpoint unless that branch changes. Start reading `RepositorySyncService.java`, then `RepositoryPollingService.java`, `service/github/GitHubWebhookService.java`, and `service/git/GitRepositoryService.java`.

## Database and configuration

- Flyway migrations: `src/main/resources/db/migration/V1__create_repositories.sql` and `V2__add_repository_monitoring.sql`.
- Persistent classes: `RepositoryEntity`, `GitHubInstallationEntity`; repositories: `RepositoryJpaRepository`, `GitHubInstallationJpaRepository`.
- `src/main/resources/application.properties` configures `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, Git repository directory, poll interval, GitHub App values, and frontend origin.
- `spring.jpa.hibernate.ddl-auto=validate` and `spring.flyway.enabled=true`: schema changes should be added as new numbered Flyway migrations.
- Defaults in this snapshot are development-oriented. Set a strong `GITHUB_WEBHOOK_SECRET`, database password, and private key securely for deployment; never expose installation tokens to the browser or persist them.

## Common extension recipes

**Add a commit API field:** extend the relevant DTO in `dto/`, populate it in `service/git/`, confirm the controller returns it, then update frontend `types/` and API consumers.

**Add a metric:** implement or extend logic in `service/git/metrics/`, register/aggregate it through `GitMetricsService`, expose it via `CommitController`, and add tests for empty history, merges, renames, and large repositories as applicable.

**Add a monitoring mode:** keep trigger handling separate from `RepositorySyncService`; preserve one synchronization path, signature verification for webhooks, short-lived token handling, and the tracked-branch checkpoint semantics.

**Add stored data:** create a new `V3__...sql` (or next available migration), update entity/repository/DTO/service layers, and test migration against a clean database and an upgraded database.

**Add AI analysis later:** create separate analysis services, prompt/schema versioning and caching; preserve original Git evidence and label LLM results as interpretations. The supplied backend snapshot does not establish that these services are implemented.

## Testing and debugging

Existing tests include `BackendApplicationTests`, `RepositorySyncServiceTest`, `RepositoryPollingServiceTest`, `GitHubWebhookServiceTest`, and `GitHubWebhookSignatureServiceTest`. Run with the actual project's Maven/Gradle test command (build files were not uploaded). For debugging, follow `controller → service → git/JPA`, inspect application logs, verify database connectivity and Flyway migrations, and check webhook signature/branch conditions before investigating the UI.
