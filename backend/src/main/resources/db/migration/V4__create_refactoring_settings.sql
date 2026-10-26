-- Additive migration: never edit an already-applied V3.
CREATE TABLE refactoring_repository_settings (
  repository_id BIGINT PRIMARY KEY REFERENCES repositories(id) ON DELETE CASCADE,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
