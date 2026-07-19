CREATE TABLE buddy_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_profile_id BIGINT NOT NULL,
    target_message_id BIGINT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_buddy_report_idempotency
        UNIQUE (reporter_user_id, idempotency_key),
    CONSTRAINT fk_buddy_report_reporter
        FOREIGN KEY (reporter_user_id) REFERENCES users (id),
    CONSTRAINT fk_buddy_report_target_profile
        FOREIGN KEY (target_profile_id) REFERENCES buddy_profiles (id),
    CONSTRAINT fk_buddy_report_target_message
        FOREIGN KEY (target_message_id) REFERENCES buddy_messages (id),
    CONSTRAINT chk_buddy_report_target CHECK (
        (target_type = 'PROFILE' AND target_message_id IS NULL)
        OR (target_type = 'MESSAGE' AND target_message_id IS NOT NULL)
    ),
    CONSTRAINT chk_buddy_report_reason CHECK (
        CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 500
    ),
    CONSTRAINT chk_buddy_report_status CHECK (
        status IN ('RECEIVED', 'REVIEWING', 'RESOLVED', 'REJECTED')
    ),
    CONSTRAINT chk_buddy_report_idempotency_key_length CHECK (
        CHAR_LENGTH(idempotency_key) BETWEEN 8 AND 100
    ),
    CONSTRAINT chk_buddy_report_request_hash_length CHECK (
        CHAR_LENGTH(request_hash) = 64
    )
);

CREATE INDEX idx_buddy_reports_status_created
    ON buddy_reports (status, created_at DESC, id DESC);
CREATE INDEX idx_buddy_reports_target_profile_created
    ON buddy_reports (target_profile_id, created_at DESC, id DESC);
CREATE INDEX idx_buddy_reports_target_message
    ON buddy_reports (target_message_id);
