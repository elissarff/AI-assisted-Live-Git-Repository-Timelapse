-- NULL uses the normal worker limit; a user can explicitly request a longer retry.
ALTER TABLE refactoring_jobs ADD COLUMN timeout_seconds INTEGER
    CHECK (timeout_seconds BETWEEN 1 AND 300);
