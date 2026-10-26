CREATE TABLE ai_repository_settings (
    repository_id BIGINT PRIMARY KEY REFERENCES repositories(id) ON DELETE CASCADE,
    auto_analyze BOOLEAN NOT NULL DEFAULT FALSE,
    historical_backfill BOOLEAN NOT NULL DEFAULT FALSE,
    monthly_token_limit BIGINT NOT NULL DEFAULT 200000 CHECK (monthly_token_limit >= 0),
    max_auto_commits_per_day INTEGER NOT NULL DEFAULT 50 CHECK (max_auto_commits_per_day >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
