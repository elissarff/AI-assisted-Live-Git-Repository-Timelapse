-- AI explanations are immutable by evidence and prompt/model profile.
CREATE TABLE ai_analysis_profiles (
 id BIGSERIAL PRIMARY KEY,
 provider TEXT NOT NULL,
 model TEXT NOT NULL,
 prompt_version TEXT NOT NULL,
 schema_version TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(provider, model, prompt_version, schema_version)
);
CREATE TABLE ai_analyses (
 id BIGSERIAL PRIMARY KEY,
 repository_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
 profile_id BIGINT NOT NULL REFERENCES ai_analysis_profiles(id),
 target_type TEXT NOT NULL CHECK(target_type IN ('COMMIT','FILE_SNAPSHOT','FILE_CHANGE','FILE_EVOLUTION','REPOSITORY','PERIOD')),
 target_key TEXT NOT NULL,
 analysis_type TEXT NOT NULL,
 evidence_hash TEXT NOT NULL,
 result JSONB NOT NULL,
 category TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(repository_id,profile_id,target_type,target_key,analysis_type,evidence_hash)
);
CREATE INDEX ai_analyses_lookup ON ai_analyses(repository_id,target_type,target_key,created_at DESC);
CREATE INDEX ai_analyses_category ON ai_analyses(repository_id,category);
CREATE TABLE ai_analysis_jobs (
 id BIGSERIAL PRIMARY KEY,
 repository_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
 profile_id BIGINT NOT NULL REFERENCES ai_analysis_profiles(id),
 target_type TEXT NOT NULL,
 target_key TEXT NOT NULL,
 analysis_type TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
 priority INTEGER NOT NULL DEFAULT 0,
 attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 leased_until TIMESTAMPTZ,
 error TEXT,
 analysis_id BIGINT REFERENCES ai_analyses(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(repository_id,profile_id,target_type,target_key,analysis_type)
);
CREATE INDEX ai_jobs_ready ON ai_analysis_jobs(status,next_attempt_at,priority DESC);
CREATE TABLE ai_analysis_dependencies (
 analysis_id BIGINT NOT NULL REFERENCES ai_analyses(id) ON DELETE CASCADE,
 source_type TEXT NOT NULL,
 source_key TEXT NOT NULL,
 source_hash TEXT,
 PRIMARY KEY(analysis_id,source_type,source_key)
);
CREATE TABLE ai_usage (
 id BIGSERIAL PRIMARY KEY,
 repository_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
 job_id BIGINT REFERENCES ai_analysis_jobs(id) ON DELETE SET NULL,
 provider TEXT NOT NULL,
 model TEXT NOT NULL,
 input_tokens INTEGER,
 output_tokens INTEGER,
 elapsed_ms BIGINT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
