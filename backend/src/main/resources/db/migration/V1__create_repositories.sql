CREATE TABLE repositories (
    id BIGSERIAL PRIMARY KEY,
    repo_key UUID NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    remote_url TEXT NOT NULL UNIQUE,
    default_branch VARCHAR(255),
    local_git_directory TEXT NOT NULL,
    last_processed_sha VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);