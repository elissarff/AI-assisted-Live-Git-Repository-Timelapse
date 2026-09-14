# Shared repository monitoring implementation

This backend now routes manual sync, GitHub webhook sync, and scheduled public-repository polling through `RepositorySyncService` and `GitService`.

## Main changes

- Added `MonitoringType`: `WEBHOOK`, `GITHUB_APP`, `POLLING`.
- Added `RepositoryVisibility`: `PUBLIC`, `PRIVATE`.
- Added GitHub installation persistence and a V2 Flyway migration.
- Added provider repository ID, full name, monitoring mode, visibility, installation association, and last-checked time to repositories.
- Added `RepositorySyncService` with a per-repository in-process lock.
- Added polling via Spring `@Scheduled`.
- Refactored JGit clone/fetch to accept optional credentials.
- Added GitHub App JWT + short-lived installation-token exchange.
- Moved GitHub webhook logic to `service/github` and made the controller thin.
- Webhooks match on GitHub repository ID first, then full name as a registration fallback.
- Added safe force-push/stale-SHA resynchronization.
- Added focused unit tests.

## Start PostgreSQL

```bash
cp .env.example .env
# edit .env as needed
docker compose --env-file .env up -d postgres
```

Load environment variables before starting Spring:

```bash
set -a
source .env
set +a
```

## Run tests

```bash
./mvnw test
```

## Run the backend

```bash
./mvnw spring-boot:run
```

Flyway will apply `V1__create_repositories.sql` and then `V2__add_repository_monitoring.sql`. Hibernate remains on `ddl-auto=validate`.

## Register an unowned public repository for polling

```bash
curl -X POST http://localhost:8080/api/repositories \
  -H 'Content-Type: application/json' \
  -d '{
    "remoteUrl": "https://github.com/owner/repository.git",
    "monitoringType": "POLLING",
    "visibility": "PUBLIC"
  }'
```

## Register an owner-controlled public repository for a normal webhook

If you know the GitHub numeric repository ID, include it. If omitted, `fullName` is used as a webhook lookup fallback and the numeric ID is learned on the first matching push.

```bash
curl -X POST http://localhost:8080/api/repositories \
  -H 'Content-Type: application/json' \
  -d '{
    "remoteUrl": "https://github.com/owner/repository.git",
    "monitoringType": "WEBHOOK",
    "visibility": "PUBLIC",
    "providerRepositoryId": 123456789,
    "fullName": "owner/repository"
  }'
```

Configure GitHub to POST push events to:

```text
POST http(s)://YOUR_HOST/api/webhooks/github
```

using the same secret as `GITHUB_WEBHOOK_SECRET`.

## Register a GitHub App installation

GitHub `installation` webhooks can populate this automatically. For local/manual testing you can also register one explicitly:

```bash
curl -X POST http://localhost:8080/api/github/installations \
  -H 'Content-Type: application/json' \
  -d '{
    "installationId": 987654321,
    "accountId": 123456,
    "accountLogin": "owner"
  }'
```

## Register a private GitHub App repository

Set `GITHUB_APP_ID` and `GITHUB_APP_PRIVATE_KEY` first. The private key may be normal PKCS#8 (`BEGIN PRIVATE KEY`) or GitHub's common PKCS#1 RSA form (`BEGIN RSA PRIVATE KEY`).

```bash
curl -X POST http://localhost:8080/api/repositories \
  -H 'Content-Type: application/json' \
  -d '{
    "remoteUrl": "https://github.com/owner/private-repository.git",
    "monitoringType": "GITHUB_APP",
    "visibility": "PRIVATE",
    "installationId": 987654321,
    "providerRepositoryId": 55555555,
    "fullName": "owner/private-repository"
  }'
```

`GitHubTokenService` creates an app JWT, requests a short-lived installation access token, and passes the token only to JGit. The installation token is not persisted.

## Manual sync

Use the UUID returned as `id` by repository registration:

```bash
curl -X POST http://localhost:8080/api/repositories/REPOSITORY_UUID/sync
```

## Inspect a repository

```bash
curl http://localhost:8080/api/repositories/REPOSITORY_UUID
```

## Polling interval

Default: 5 minutes.

```text
REPOSITORY_POLL_INTERVAL_MS=300000
```

Only repositories whose `monitoring_type` is `POLLING` are selected by the scheduler.

## Important implementation boundary

The current project does not yet have a separate commit-persistence/AI-processing service. `RepositorySyncService` currently determines the new commits and returns them in `SyncResultDto`, then advances `lastProcessedSha`. The marked hook in `RepositorySyncService` is the place to connect the future commit/AI persistence pipeline without duplicating Git synchronization.
