# Frontend Developer Guide

## Purpose

The frontend is a React + TypeScript application with React Router, SCSS, and a Vite-style environment configuration. It provides repository discovery, GitHub setup, repository exploration, timeline controls, commit details, file diffs, trees, and metrics.

## Navigation and routes

`src/main.tsx` boots the app; `src/App.tsx` defines routes:

| Route | Page |
|---|---|
| `/` | Redirects to `/home` |
| `/home` | `HomePage.tsx` |
| `/github/setup` | `GitHubSetupPage.tsx` |
| `/repositories` | `RepositoriesPage.tsx` |
| `/repositories/:repoKey` | `RepositoryPage.tsx` |
| Other paths | Redirect to `/home` |

Start at `src/pages/RepositoryPage.tsx` to understand the main repository experience; trace its imports into `components/` and `api/`.

## File guide

| Location | Role |
|---|---|
| `src/components/TimeLine.tsx` | Timeline display and interaction |
| `src/components/TimelineFilters.tsx` | Timeline filtering controls |
| `src/components/CommitSummary.tsx` | Commit summary display |
| `src/components/FileDiff.tsx` | Diff presentation |
| `src/components/RepositoryTree.tsx` | Tree visualization |
| `src/components/MetricsExplorer.tsx` | Repository metrics exploration |
| `src/components/RepositoryHeader.tsx` | Repository header |
| `src/components/ConnectGitHubButton.tsx` | GitHub connection UI |
| `src/api/repositories.ts` | Repository requests |
| `src/api/commits.ts` | Commit requests |
| `src/api/github.ts` | GitHub requests |
| `src/api/api.ts` | Shared request/error utilities |
| `src/types/repository.ts`, `git.ts`, `github.ts` | Data contracts |
| `src/config.ts` | API base URL and GitHub App install URL |
| `src/index.scss` and component `.scss` files | Global and local styling |

## Data flow

```text
App.tsx route → pages/RepositoryPage.tsx
               → components/TimeLine, CommitSummary, FileDiff, RepositoryTree, MetricsExplorer
               → api/commits.ts or api/repositories.ts
               → backend /api/repositories/... endpoints
               → typed data from types/*.ts
```

When debugging a missing value, inspect the network response, the API function, the corresponding TypeScript type, and finally the component props/state.

## Configuration and startup

`src/config.ts` uses `VITE_API_BASE_URL` (default `http://localhost:8080`) and `VITE_GITHUB_APP_INSTALL_URL` (default points to the project's development GitHub App installation page). Put environment-specific values in a Vite `.env.local` file in the frontend project root, for example:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_GITHUB_APP_INSTALL_URL=https://github.com/apps/YOUR_APP_SLUG/installations/new
```

The uploaded frontend archive contains only `src/`; restore `package.json`, Vite configuration, and other project-root files before running install/dev/build scripts. Check `src/api/api.ts`: the shared `request()` currently calls `fetch(endpoint)` directly, so verify URL composition or the Vite proxy when changing API hosting.

## Adding a new feature

**New backend-backed widget:** identify the endpoint; add a typed request function in `src/api/`, update `src/types/`, build a component in `src/components/` with its own SCSS if needed, and render it from the relevant page. Handle loading, empty, and failure states.

**New page:** add `src/pages/NewPage.tsx`, import it into `App.tsx`, register a route, and add navigation from an existing page.

**New timeline control/filter:** inspect `TimeLine.tsx`, `TimelineFilters.tsx`, and `RepositoryPage.tsx` to identify where selection and filter state are owned; update the API request/query parameters and ensure selected-commit details stay synchronized.

**New metrics visualization:** inspect `MetricsExplorer.tsx`, the metrics API in `src/api/commits.ts`, and backend `GitMetricsService`; keep metric names/response shapes aligned.

**AI-assisted feature later:** add a dedicated typed API endpoint and component; clearly distinguish generated interpretations from raw commit facts. The proposal's semantic search and LLM features are roadmap items, not confirmed frontend functionality in this snapshot.

## Verification checklist

Check route navigation, repository loading, timeline playback and selection, commit details, diffs and trees, metrics, error states, and behavior after a repository sync. Test with empty and large repositories. The provided archive does not contain frontend test configuration, so consult the full project before prescribing a test command.
