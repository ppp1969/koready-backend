ALTER TABLE place_editorial_jobs
    ADD COLUMN lease_token CHAR(36) NULL AFTER attempt_count,
    ADD COLUMN lease_expires_at TIMESTAMP(6) NULL AFTER lease_token,
    ADD COLUMN next_attempt_at TIMESTAMP(6) NULL AFTER lease_expires_at,
    ADD COLUMN provider VARCHAR(100) NULL AFTER error_message,
    ADD COLUMN model VARCHAR(191) NULL AFTER provider,
    ADD COLUMN input_tokens INT NULL AFTER model,
    ADD COLUMN output_tokens INT NULL AFTER input_tokens;

CREATE INDEX ix_place_editorial_jobs_worker
    ON place_editorial_jobs (status, next_attempt_at, priority DESC, requested_at, id);

CREATE INDEX ix_place_editorial_jobs_lease
    ON place_editorial_jobs (status, lease_expires_at);

CREATE INDEX ix_place_editorial_audits_action_time
    ON place_editorial_audits (action, created_at);
