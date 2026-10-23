# Git Repository Timelapse — Project Guide

A full-stack application for exploring Git repository evolution, commit details, file changes, metrics, and repository monitoring. The COMP 490 proposal also describes future AI-assisted explanations, embeddings, and semantic evolution search; **do not assume these are implemented in the provided source snapshots**.

## Documentation map

- [Backend developer guide](backend/README.md): Java services, REST endpoints, Git extraction, sync, PostgreSQL, and extension points.
- [Frontend developer guide](frontend/README.md): React pages, components, API clients, types, and feature workflows.

## Source snapshots and layout

The supplied archives contain **`src/` directories only**, not complete build roots. Place the backend `src/` under its Spring Boot project root (with `pom.xml` or `build.gradle`), and the frontend `src/` under its React project root (with `package.json`, Vite config, and dependencies). Those root build files were not included; exact installation/build commands and dependency versions cannot be verified from these uploads.

```text
project/
├── backend/
│   ├── src/main/java/com/timelapse/backend/
│   │   ├── controller/   # HTTP entry points
│   │   ├── service/      # orchestration and business logic
│   │   │   ├── git/      # Git extraction, diff, trees, metrics
│   │   │   └── github/   # GitHub integration and webhooks
│   │   ├── dto/          # API data contracts
│   │   ├── entity/       # persisted records
│   │   ├── repository/   # JPA access
│   │   ├── config/       # application configuration
│   │   └── types/        # shared enums
│   ├── src/main/resources/db/migration/
│   └── src/test/java/
└── frontend/
    └── src/
        ├── pages/       # routed screens
        ├── components/  # UI building blocks
        ├── api/         # HTTP calls
        ├── types/       # TypeScript contracts
        ├── App.tsx      # routes
        └── config.ts    # API and GitHub App URLs
```

## Runtime architecture

```text
React pages and components
         ↓
frontend/src/api/* → Spring Boot controllers → services
                                              ├── git/* → Git repository data
                                              ├── github/* → GitHub API/webhooks
                                              └── JPA → PostgreSQL (Flyway schema)
```

Git remains the authoritative source of commit history; PostgreSQL stores repository and monitoring state. The proposal describes incremental synchronization of the tracked default branch, using polling, signed webhooks, or a GitHub App installation as triggers.

## Local setup checklist

1. Restore the complete frontend and backend project roots (the uploaded archives omit build manifests).
2. Start PostgreSQL and create the database/user configured in `backend/src/main/resources/application.properties` (defaults: database `git_timelapse`, user `timelapse`). Avoid committing real credentials.
3. Set backend environment variables as needed: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REPOSITORIES_DIRECTORY`, `REPOSITORY_POLL_INTERVAL_MS`, `FRONTEND_ORIGIN`, `GITHUB_APP_ID`, `GITHUB_APP_PRIVATE_KEY`, `GITHUB_WEBHOOK_SECRET`, `GITHUB_APP_SLUG`.
4. Start Spring Boot using the project's actual Maven/Gradle setup; the backend configuration assumes port 8080 unless overridden. Check `GET /health`.
5. Configure the frontend `VITE_API_BASE_URL` (default `http://localhost:8080`) and optional `VITE_GITHUB_APP_INSTALL_URL`; start Vite using the frontend project's actual package scripts (typically `npm install` then `npm run dev`). The backend CORS origin defaults to `http://localhost:5173`.
6. Open the frontend and test repository listing, repository selection, commit navigation, and sync.

**Note:** The supplied frontend API helper should be reviewed when configuring the base URL: it uses `fetch(endpoint)` directly in the snapshot, so verify that callers provide full URLs or that a development proxy is configured.

## How to navigate a request

For a UI feature, start at `frontend/src/App.tsx`, open the matching `pages/*Page.tsx`, trace its `components/` and `api/` imports, then follow the requested backend URL to `controller/`, `service/`, and the relevant `dto/` or Git/JPA implementation.

## Adding a feature end-to-end

1. Describe the user interaction and the required Git/database information.
2. Define or extend the backend DTO and controller endpoint; put Git operations in `service/git/` and orchestration in `service/`.
3. Add a Flyway migration if persistence changes; avoid modifying migrations already applied.
4. Add a typed frontend API function under `src/api/` and matching types under `src/types/`.
5. Implement the page/component and connect loading, error, and empty states.
6. Add backend unit/integration tests and verify the complete user flow manually; add frontend tests if the full project has a test runner.
7. Update the relevant README, including configuration and endpoint changes.

## Scope and implementation status

The uploaded source shows repository pages, commit/timeline UI, metrics, GitHub integration, Git extraction, polling/webhook sync, and PostgreSQL migrations. The proposal additionally plans LLM commit interpretation, pgvector embeddings, semantic search, and advanced summaries. Treat proposal items as roadmap until corresponding source code and tests exist.

## Suggested first reads

`frontend/src/App.tsx` → `frontend/src/pages/RepositoryPage.tsx` → `frontend/src/api/commits.ts` → `backend/.../controller/CommitController.java` → `backend/.../service/git/GitCommitService.java`.
