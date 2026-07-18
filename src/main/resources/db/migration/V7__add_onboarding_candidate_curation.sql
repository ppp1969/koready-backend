CREATE TABLE onboarding_candidate_sets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(100) NOT NULL,
    title VARCHAR(100) NOT NULL,
    version BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    published_by_subject VARCHAR(191) NULL,
    published_at DATETIME(6) NULL,
    archived_by_subject VARCHAR(191) NULL,
    archived_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_onboarding_candidate_set_public_id UNIQUE (public_id),
    CONSTRAINT uq_onboarding_candidate_set_version UNIQUE (version),
    CONSTRAINT chk_onboarding_candidate_set_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_onboarding_candidate_set_version
        CHECK (version IS NULL OR version >= 1),
    CONSTRAINT chk_onboarding_candidate_set_publish_state
        CHECK (
            (status = 'DRAFT' AND published_at IS NULL AND archived_at IS NULL)
            OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND archived_at IS NULL)
            OR (status = 'ARCHIVED' AND archived_at IS NOT NULL)
        )
);

CREATE INDEX idx_onboarding_candidate_set_status
    ON onboarding_candidate_sets (status, id DESC);

CREATE TABLE onboarding_candidate_set_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    candidate_set_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    display_order TINYINT NOT NULL,
    representative_image_id BIGINT NULL,
    curator_message_ko VARCHAR(160) NOT NULL,
    curator_message_en VARCHAR(240) NULL,
    display_tags_json JSON NOT NULL,
    editor_note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_onboarding_candidate_item_place
        UNIQUE (candidate_set_id, place_id),
    CONSTRAINT uq_onboarding_candidate_item_order
        UNIQUE (candidate_set_id, display_order),
    CONSTRAINT fk_onboarding_candidate_item_set
        FOREIGN KEY (candidate_set_id) REFERENCES onboarding_candidate_sets (id),
    CONSTRAINT fk_onboarding_candidate_item_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT chk_onboarding_candidate_item_order
        CHECK (display_order BETWEEN 1 AND 10),
    CONSTRAINT chk_onboarding_candidate_item_tags
        CHECK (JSON_TYPE(display_tags_json) = 'ARRAY'
            AND JSON_LENGTH(display_tags_json) <= 5)
);

CREATE TABLE onboarding_candidate_current (
    slot TINYINT NOT NULL,
    candidate_set_id BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (slot),
    CONSTRAINT uq_onboarding_candidate_current_set UNIQUE (candidate_set_id),
    CONSTRAINT fk_onboarding_candidate_current_set
        FOREIGN KEY (candidate_set_id) REFERENCES onboarding_candidate_sets (id),
    CONSTRAINT chk_onboarding_candidate_current_slot CHECK (slot = 1)
);

CREATE TABLE admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_subject VARCHAR(191) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(191) NOT NULL,
    before_status VARCHAR(30) NULL,
    after_status VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_admin_audit_resource
    ON admin_audit_logs (resource_type, resource_id, id DESC);
CREATE INDEX idx_admin_audit_actor_time
    ON admin_audit_logs (actor_subject, created_at DESC);
