CREATE TABLE github_installations (
    id BIGSERIAL PRIMARY KEY,
    github_installation_id BIGINT NOT NULL UNIQUE,
    github_account_id BIGINT,
    github_account_login VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE repositories
    ADD COLUMN provider_repository_id BIGINT,
    ADD COLUMN full_name VARCHAR(512),
    ADD COLUMN monitoring_type VARCHAR(32) NOT NULL DEFAULT 'POLLING',
    ADD COLUMN visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN installation_id BIGINT,
    ADD COLUMN last_checked_at TIMESTAMPTZ;

ALTER TABLE repositories
    ADD CONSTRAINT uq_repositories_provider_repository_id UNIQUE (provider_repository_id),
    ADD CONSTRAINT fk_repositories_installation
        FOREIGN KEY (installation_id) REFERENCES github_installations(id);

CREATE INDEX idx_repositories_provider_repository_id ON repositories(provider_repository_id);
CREATE INDEX idx_repositories_monitoring_type ON repositories(monitoring_type);
CREATE INDEX idx_repositories_installation_id ON repositories(installation_id);
