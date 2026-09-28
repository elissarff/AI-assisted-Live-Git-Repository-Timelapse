# GitHub App frontend flow

This React/Vite structure handles:

1. `/home` -> Connect GitHub button
2. GitHub App installation page
3. GitHub redirects to `/github/setup?installation_id=...`
4. frontend calls `POST /api/github/installations/{installationId}/connect`
5. frontend loads `GET /api/github/installations/{installationId}/repositories`
6. user chooses a repository
7. frontend calls `POST /api/repositories`
8. frontend redirects to `/repositories/{repoKey}`
9. frontend calls `GET /api/repositories/{repoKey}`

## GitHub App setting

Set the GitHub App **Setup URL** to:

`http://localhost:5173/github/setup`

## Environment

Copy:

```bash
cp .env.example .env
```

Then make sure `VITE_GITHUB_APP_INSTALL_URL` uses the exact GitHub App slug.

## Run

```bash
npm install
npm run dev
```

## Backend endpoints expected

- `POST /api/github/installations/{installationId}/connect`
- `GET /api/github/installations/{installationId}/repositories`
- `POST /api/repositories`
- `GET /api/repositories/{repoKey}`

The first two endpoints are the clean frontend-facing API discussed for replacing the manual account ID/login registration.
