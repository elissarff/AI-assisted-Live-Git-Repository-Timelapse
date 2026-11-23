CREATE TABLE ai_context_results (
 id BIGSERIAL PRIMARY KEY,
 repository_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
 target_type TEXT NOT NULL,
 target_key TEXT NOT NULL,
 profile TEXT NOT NULL,
 evidence_hash TEXT NOT NULL,
 result JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(repository_id,target_type,target_key,profile,evidence_hash)
);
CREATE INDEX ai_context_lookup ON ai_context_results(repository_id,target_type,profile,target_key);
