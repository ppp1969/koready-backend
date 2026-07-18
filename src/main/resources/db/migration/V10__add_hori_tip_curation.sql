CREATE TABLE hori_tips (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    source VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    placement VARCHAR(30) NOT NULL,
    priority SMALLINT UNSIGNED NOT NULL,
    scope_type VARCHAR(30) NOT NULL,
    segment_modes_json JSON NOT NULL,
    route_name_contains_json JSON NOT NULL,
    segment_start_name_contains_json JSON NOT NULL,
    segment_end_name_contains_json JSON NOT NULL,
    min_provider_total_time_seconds INT NULL,
    min_transfer_count INT NULL,
    min_total_walk_distance_meters INT NULL,
    valid_from DATETIME(6) NULL,
    valid_until DATETIME(6) NULL,
    operator_note VARCHAR(500) NULL,
    version INT NOT NULL,
    created_by_subject VARCHAR(191) NOT NULL,
    updated_by_subject VARCHAR(191) NOT NULL,
    activated_at DATETIME(6) NULL,
    archived_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_hori_tip_code UNIQUE (code),
    CONSTRAINT chk_hori_tip_code
        CHECK (code REGEXP '^TIP_[A-Z0-9_]+$'),
    CONSTRAINT chk_hori_tip_source
        CHECK (source = 'OPERATOR_CURATED'),
    CONSTRAINT chk_hori_tip_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_hori_tip_placement
        CHECK (placement IN ('TOP_SUMMARY', 'AFTER_SEGMENT')),
    CONSTRAINT chk_hori_tip_priority CHECK (priority BETWEEN 0 AND 1000),
    CONSTRAINT chk_hori_tip_scope
        CHECK (scope_type IN ('ALL_ROUTES', 'DESTINATION_PLACES')),
    CONSTRAINT chk_hori_tip_trigger_arrays CHECK (
        JSON_TYPE(segment_modes_json) = 'ARRAY'
        AND JSON_LENGTH(segment_modes_json) <= 8
        AND JSON_TYPE(route_name_contains_json) = 'ARRAY'
        AND JSON_LENGTH(route_name_contains_json) <= 20
        AND JSON_TYPE(segment_start_name_contains_json) = 'ARRAY'
        AND JSON_LENGTH(segment_start_name_contains_json) <= 20
        AND JSON_TYPE(segment_end_name_contains_json) = 'ARRAY'
        AND JSON_LENGTH(segment_end_name_contains_json) <= 20
    ),
    CONSTRAINT chk_hori_tip_thresholds CHECK (
        (min_provider_total_time_seconds IS NULL
            OR min_provider_total_time_seconds >= 0)
        AND (min_transfer_count IS NULL OR min_transfer_count >= 0)
        AND (min_total_walk_distance_meters IS NULL
            OR min_total_walk_distance_meters >= 0)
    ),
    CONSTRAINT chk_hori_tip_period
        CHECK (valid_from IS NULL OR valid_until IS NULL OR valid_from < valid_until),
    CONSTRAINT chk_hori_tip_version CHECK (version >= 1),
    CONSTRAINT chk_hori_tip_activation CHECK (
        status <> 'ACTIVE' OR activated_at IS NOT NULL
    ),
    CONSTRAINT chk_hori_tip_archive CHECK (
        (status = 'ARCHIVED' AND archived_at IS NOT NULL)
        OR (status <> 'ARCHIVED' AND archived_at IS NULL)
    )
);

CREATE INDEX idx_hori_tip_status_id ON hori_tips (status, id DESC);

CREATE TABLE hori_tip_destination_places (
    hori_tip_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (hori_tip_id, place_id),
    CONSTRAINT fk_hori_tip_destination_tip
        FOREIGN KEY (hori_tip_id) REFERENCES hori_tips (id),
    CONSTRAINT fk_hori_tip_destination_place
        FOREIGN KEY (place_id) REFERENCES places (id)
);

CREATE INDEX idx_hori_tip_destination_place
    ON hori_tip_destination_places (place_id, hori_tip_id DESC);

CREATE TABLE hori_tip_translations (
    hori_tip_id BIGINT NOT NULL,
    language VARCHAR(10) NOT NULL,
    body VARCHAR(300) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (hori_tip_id, language),
    CONSTRAINT fk_hori_tip_translation_tip
        FOREIGN KEY (hori_tip_id) REFERENCES hori_tips (id),
    CONSTRAINT chk_hori_tip_translation_language
        CHECK (language IN ('KO', 'EN'))
);

CREATE TABLE hori_tip_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hori_tip_id BIGINT NOT NULL,
    actor_subject VARCHAR(191) NOT NULL,
    action VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NULL,
    before_status VARCHAR(20) NULL,
    after_status VARCHAR(20) NULL,
    before_version INT NULL,
    after_version INT NULL,
    before_snapshot JSON NULL,
    after_snapshot JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_hori_tip_audit_tip
        FOREIGN KEY (hori_tip_id) REFERENCES hori_tips (id)
);

CREATE INDEX idx_hori_tip_audit_resource
    ON hori_tip_audit_logs (hori_tip_id, id DESC);
CREATE INDEX idx_hori_tip_audit_actor_time
    ON hori_tip_audit_logs (actor_subject, created_at DESC);
