CREATE TABLE refactoring_jobs (
 id BIGSERIAL PRIMARY KEY,
 repository_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
 commit_sha VARCHAR(40) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 attempts INTEGER NOT NULL DEFAULT 0,
 requested_explicitly BOOLEAN NOT NULL DEFAULT FALSE,
 last_error TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(repository_id, commit_sha)
);
CREATE INDEX idx_refactoring_jobs_status ON refactoring_jobs(status, updated_at);
CREATE TABLE detected_refactorings (
 id BIGSERIAL PRIMARY KEY,
 job_id BIGINT NOT NULL REFERENCES refactoring_jobs(id) ON DELETE CASCADE,
 parent_sha VARCHAR(40),
 type TEXT NOT NULL,
 description TEXT NOT NULL,
 before_locations JSONB NOT NULL DEFAULT '[]'::jsonb,
 after_locations JSONB NOT NULL DEFAULT '[]'::jsonb
);
CREATE INDEX idx_detected_refactorings_job ON detected_refactorings(job_id);
