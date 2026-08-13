CREATE TABLE place_editorial_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    place_id BIGINT NOT NULL,
    request_key CHAR(64) NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    priority SMALLINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    requested_by_subject VARCHAR(191) NULL,
    error_code VARCHAR(100) NULL,
    error_message VARCHAR(1000) NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_place_editorial_jobs_public_id (public_id),
    UNIQUE KEY uq_place_editorial_jobs_request_key (request_key),
    KEY ix_place_editorial_jobs_poll (status, priority DESC, requested_at, id),
    KEY ix_place_editorial_jobs_place (place_id, requested_at DESC),
    CONSTRAINT fk_place_editorial_jobs_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT ck_place_editorial_jobs_trigger
        CHECK (trigger_type IN ('PM_CURATED', 'USER_DETAIL')),
    CONSTRAINT ck_place_editorial_jobs_status
        CHECK (status IN ('QUEUED', 'PROCESSING', 'READY', 'FAILED', 'STALE')),
    CONSTRAINT ck_place_editorial_jobs_priority
        CHECK (priority IN (50, 100)),
    CONSTRAINT ck_place_editorial_jobs_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE TABLE place_editorial_contents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider VARCHAR(100) NULL,
    model VARCHAR(191) NULL,
    generated_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_place_editorial_contents_version
        (place_id, source_fingerprint, prompt_version),
    KEY ix_place_editorial_contents_public
        (place_id, status, prompt_version, generated_at DESC),
    CONSTRAINT fk_place_editorial_contents_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT ck_place_editorial_contents_status
        CHECK (status IN ('READY', 'STALE'))
);

CREATE TABLE place_editorial_localizations (
    editorial_content_id BIGINT NOT NULL,
    language VARCHAR(10) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    one_line_description VARCHAR(300) NOT NULL,
    short_introduction VARCHAR(1000) NOT NULL,
    PRIMARY KEY (editorial_content_id, language),
    CONSTRAINT fk_place_editorial_localizations_content
        FOREIGN KEY (editorial_content_id)
        REFERENCES place_editorial_contents (id) ON DELETE CASCADE,
    CONSTRAINT ck_place_editorial_localizations_language
        CHECK (language IN ('KO', 'EN'))
);

CREATE TABLE place_editorial_tags (
    editorial_content_id BIGINT NOT NULL,
    display_order SMALLINT NOT NULL,
    tag_code VARCHAR(30) NOT NULL,
    PRIMARY KEY (editorial_content_id, display_order),
    UNIQUE KEY uq_place_editorial_tags_code (editorial_content_id, tag_code),
    CONSTRAINT fk_place_editorial_tags_content
        FOREIGN KEY (editorial_content_id)
        REFERENCES place_editorial_contents (id) ON DELETE CASCADE,
    CONSTRAINT ck_place_editorial_tags_order CHECK (display_order BETWEEN 1 AND 2)
);

CREATE TABLE place_editorial_enjoy_points (
    editorial_content_id BIGINT NOT NULL,
    language VARCHAR(10) NOT NULL,
    display_order SMALLINT NOT NULL,
    content VARCHAR(300) NOT NULL,
    PRIMARY KEY (editorial_content_id, language, display_order),
    CONSTRAINT fk_place_editorial_enjoy_points_content
        FOREIGN KEY (editorial_content_id)
        REFERENCES place_editorial_contents (id) ON DELETE CASCADE,
    CONSTRAINT ck_place_editorial_enjoy_points_language
        CHECK (language IN ('KO', 'EN')),
    CONSTRAINT ck_place_editorial_enjoy_points_order
        CHECK (display_order BETWEEN 1 AND 5)
);

CREATE TABLE place_editorial_audits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    job_id BIGINT NULL,
    actor_subject VARCHAR(191) NULL,
    action VARCHAR(100) NOT NULL,
    details_json JSON NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_place_editorial_audits_place (place_id, created_at DESC),
    CONSTRAINT fk_place_editorial_audits_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT fk_place_editorial_audits_job
        FOREIGN KEY (job_id) REFERENCES place_editorial_jobs (id)
);
