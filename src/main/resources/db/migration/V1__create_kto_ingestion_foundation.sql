CREATE TABLE service_regions (
    code VARCHAR(20) NOT NULL,
    name_ko VARCHAR(50) NOT NULL,
    name_en VARCHAR(50) NOT NULL,
    display_order SMALLINT NOT NULL,
    map_asset_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (code),
    CONSTRAINT uq_service_regions_display_order UNIQUE (display_order),
    CONSTRAINT uq_service_regions_map_asset_key UNIQUE (map_asset_key)
);

CREATE TABLE administrative_regions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(20) NOT NULL,
    level VARCHAR(20) NOT NULL,
    code VARCHAR(30) NOT NULL,
    parent_code VARCHAR(30) NOT NULL DEFAULT '',
    name VARCHAR(100) NOT NULL,
    service_region_code VARCHAR(20) NOT NULL,
    center_latitude DECIMAL(10, 7) NULL,
    center_longitude DECIMAL(10, 7) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_administrative_region
        UNIQUE (provider, level, parent_code, code),
    CONSTRAINT fk_administrative_region_service_region
        FOREIGN KEY (service_region_code) REFERENCES service_regions (code),
    CONSTRAINT chk_administrative_region_level
        CHECK (level IN ('SIDO', 'SIGUNGU', 'DONG'))
);

CREATE INDEX idx_administrative_region_service
    ON administrative_regions (service_region_code, level);

CREATE TABLE batch_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    processed_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    message VARCHAR(1000) NULL,
    trigger_source VARCHAR(30) NOT NULL,
    triggered_by_user_id BIGINT NULL,
    parent_job_id BIGINT NULL,
    parameters_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_batch_job_parent
        FOREIGN KEY (parent_job_id) REFERENCES batch_jobs (id),
    CONSTRAINT chk_batch_job_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'PARTIAL_FAILED')),
    CONSTRAINT chk_batch_job_trigger
        CHECK (trigger_source IN ('SCHEDULED', 'ADMIN_MANUAL', 'RETRY')),
    CONSTRAINT chk_batch_job_counts
        CHECK (
            processed_count >= 0
            AND success_count >= 0
            AND failure_count >= 0
            AND success_count + failure_count <= processed_count
        )
);

CREATE INDEX idx_batch_jobs_status_created
    ON batch_jobs (status, created_at);

CREATE TABLE batch_job_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_job_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_batch_job_item_target
        UNIQUE (batch_job_id, target_type, target_id),
    CONSTRAINT fk_batch_job_item_job
        FOREIGN KEY (batch_job_id) REFERENCES batch_jobs (id),
    CONSTRAINT chk_batch_job_item_type
        CHECK (target_type IN ('API_PAGE', 'PLACE', 'IMAGE', 'TRANSLATION')),
    CONSTRAINT chk_batch_job_item_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_batch_job_items_status
    ON batch_job_items (batch_job_id, status, id);

CREATE TABLE open_api_call_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(20) NOT NULL,
    api_name VARCHAR(100) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    endpoint VARCHAR(500) NOT NULL,
    request_started_at DATETIME(6) NOT NULL,
    response_received_at DATETIME(6) NULL,
    duration_ms BIGINT NULL,
    success BOOLEAN NOT NULL,
    http_status SMALLINT NULL,
    request_params_masked JSON NULL,
    response_summary JSON NULL,
    external_result_code VARCHAR(100) NULL,
    item_count INT NULL,
    response_bytes BIGINT NULL,
    error_message VARCHAR(1000) NULL,
    related_job_id BIGINT NULL,
    related_job_item_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_open_api_call_job
        FOREIGN KEY (related_job_id) REFERENCES batch_jobs (id),
    CONSTRAINT fk_open_api_call_job_item
        FOREIGN KEY (related_job_item_id) REFERENCES batch_job_items (id),
    CONSTRAINT chk_open_api_call_metrics
        CHECK (
            (duration_ms IS NULL OR duration_ms >= 0)
            AND (item_count IS NULL OR item_count >= 0)
            AND (response_bytes IS NULL OR response_bytes >= 0)
        )
);

CREATE INDEX idx_open_api_call_provider_time
    ON open_api_call_logs (provider, operation, request_started_at);
CREATE INDEX idx_open_api_call_job
    ON open_api_call_logs (related_job_id, related_job_item_id);

CREATE TABLE open_api_raw_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    call_log_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    api_name VARCHAR(100) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    storage_format VARCHAR(30) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    raw_content_sha256 CHAR(64) NOT NULL,
    stored_object_sha256 CHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    compressed_byte_size BIGINT NOT NULL,
    item_count INT NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    retention_class VARCHAR(40) NOT NULL,
    retention_until DATETIME(6) NULL,
    immutable BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uq_open_api_raw_snapshot_call UNIQUE (call_log_id),
    CONSTRAINT uq_open_api_raw_snapshot_storage UNIQUE (storage_key),
    CONSTRAINT fk_open_api_raw_snapshot_call
        FOREIGN KEY (call_log_id) REFERENCES open_api_call_logs (id),
    CONSTRAINT chk_open_api_raw_snapshot_format
        CHECK (storage_format IN ('JSON_GZIP', 'XML_GZIP')),
    CONSTRAINT chk_open_api_raw_snapshot_retention
        CHECK (
            retention_class IN (
                'COMPETITION_EVIDENCE',
                'DEBUG_TEMPORARY',
                'PROVIDER_RESTRICTED'
            )
        ),
    CONSTRAINT chk_open_api_raw_snapshot_sizes
        CHECK (
            byte_size >= 0
            AND compressed_byte_size >= 0
            AND item_count >= 0
            AND immutable = TRUE
        )
);

CREATE TABLE tour_api_sync_cursors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(20) NOT NULL,
    api_name VARCHAR(100) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    cursor_type VARCHAR(30) NOT NULL,
    cursor_value VARCHAR(500) NULL,
    last_success_at DATETIME(6) NULL,
    last_failure_at DATETIME(6) NULL,
    failure_count INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_tour_api_sync_cursor
        UNIQUE (provider, api_name, operation, cursor_type),
    CONSTRAINT chk_tour_api_sync_cursor_type
        CHECK (cursor_type IN ('MODIFIED_TIME', 'PAGE', 'DATE_RANGE', 'MANUAL')),
    CONSTRAINT chk_tour_api_sync_cursor_failure_count
        CHECK (failure_count >= 0)
);

CREATE INDEX idx_tour_api_sync_cursor_enabled
    ON tour_api_sync_cursors (enabled, provider, operation);

CREATE TABLE places (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kto_content_id VARCHAR(100) NULL,
    kto_content_type_id VARCHAR(30) NULL,
    service_region_code VARCHAR(20) NULL,
    area_code VARCHAR(30) NULL,
    sigungu_code VARCHAR(30) NULL,
    ldong_regn_cd VARCHAR(30) NULL,
    ldong_signgu_cd VARCHAR(30) NULL,
    lcls_systm1 VARCHAR(30) NULL,
    lcls_systm2 VARCHAR(30) NULL,
    lcls_systm3 VARCHAR(30) NULL,
    address VARCHAR(500) NULL,
    road_address VARCHAR(500) NULL,
    latitude DECIMAL(10, 7) NULL,
    longitude DECIMAL(10, 7) NULL,
    tel VARCHAR(255) NULL,
    homepage VARCHAR(1000) NULL,
    first_image_url VARCHAR(1000) NULL,
    source_modified_time DATETIME(6) NULL,
    show_flag BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    data_quality_score DECIMAL(5, 2) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_places_kto_content_id UNIQUE (kto_content_id),
    CONSTRAINT fk_places_service_region
        FOREIGN KEY (service_region_code) REFERENCES service_regions (code),
    CONSTRAINT chk_places_data_quality_score
        CHECK (data_quality_score >= 0 AND data_quality_score <= 100),
    CONSTRAINT chk_places_latitude
        CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    CONSTRAINT chk_places_longitude
        CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180))
);

CREATE INDEX idx_places_region_active
    ON places (service_region_code, active, show_flag, id);
CREATE INDEX idx_places_area
    ON places (area_code, sigungu_code, active);

CREATE TABLE place_localizations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    language VARCHAR(10) NOT NULL,
    title VARCHAR(300) NOT NULL,
    overview TEXT NULL,
    address_text VARCHAR(500) NULL,
    translation_source VARCHAR(30) NOT NULL,
    source_content_id VARCHAR(100) NULL,
    source_hash CHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_place_localization UNIQUE (place_id, language),
    CONSTRAINT fk_place_localization_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT chk_place_localization_language
        CHECK (language IN ('KO', 'EN')),
    CONSTRAINT chk_place_localization_source
        CHECK (
            translation_source IN (
                'KTO_KO',
                'KTO_EN',
                'AI_TRANSLATED',
                'MANUAL_EDITED'
            )
        )
);

CREATE TABLE place_source_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(20) NOT NULL,
    api_name VARCHAR(100) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    source_content_id VARCHAR(100) NOT NULL,
    language VARCHAR(10) NOT NULL,
    raw_snapshot_id BIGINT NOT NULL,
    source_modified_time DATETIME(6) NULL,
    source_hash CHAR(64) NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_place_source_snapshot_record
        UNIQUE (raw_snapshot_id, language, source_content_id),
    CONSTRAINT fk_place_source_record_snapshot
        FOREIGN KEY (raw_snapshot_id) REFERENCES open_api_raw_snapshots (id),
    CONSTRAINT chk_place_source_record_language
        CHECK (language IN ('KO', 'EN'))
);

CREATE INDEX idx_place_source_provider_record
    ON place_source_records (
        provider,
        api_name,
        operation,
        language,
        source_content_id
    );

CREATE TABLE place_source_matches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_record_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    match_method VARCHAR(40) NOT NULL,
    confidence DECIMAL(5, 4) NOT NULL,
    candidate_count INT NOT NULL,
    evidence_json JSON NULL,
    status VARCHAR(30) NOT NULL,
    matcher_version VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_place_source_match
        UNIQUE (source_record_id, place_id, matcher_version),
    CONSTRAINT fk_place_source_match_record
        FOREIGN KEY (source_record_id) REFERENCES place_source_records (id),
    CONSTRAINT fk_place_source_match_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT chk_place_source_match_confidence
        CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT chk_place_source_match_candidates
        CHECK (candidate_count >= 1),
    CONSTRAINT chk_place_source_match_status
        CHECK (status IN ('AUTO_CONFIRMED', 'REVIEW_REQUIRED', 'REJECTED'))
);

CREATE INDEX idx_place_source_match_review
    ON place_source_matches (status, place_id);

CREATE TABLE festival_series (
    id BIGINT NOT NULL AUTO_INCREMENT,
    series_key VARCHAR(150) NOT NULL,
    canonical_place_id BIGINT NULL,
    title_ko VARCHAR(300) NOT NULL,
    title_en VARCHAR(300) NULL,
    match_status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_festival_series_key UNIQUE (series_key),
    CONSTRAINT fk_festival_series_place
        FOREIGN KEY (canonical_place_id) REFERENCES places (id),
    CONSTRAINT chk_festival_series_match_status
        CHECK (
            match_status IN (
                'AUTO_CONFIRMED',
                'REVIEW_REQUIRED',
                'MANUAL_CONFIRMED'
            )
        )
);

CREATE TABLE place_event_occurrences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    festival_series_id BIGINT NULL,
    place_id BIGINT NOT NULL,
    event_year SMALLINT NOT NULL,
    occurrence_sequence SMALLINT NOT NULL DEFAULT 1,
    start_date DATE NULL,
    end_date DATE NULL,
    event_place VARCHAR(500) NULL,
    play_time VARCHAR(1000) NULL,
    use_fee VARCHAR(1000) NULL,
    sponsor VARCHAR(500) NULL,
    provider VARCHAR(20) NOT NULL,
    source_content_id VARCHAR(100) NOT NULL,
    source_operation VARCHAR(100) NOT NULL,
    source_hash CHAR(64) NULL,
    visible_from DATE NULL,
    date_validation_status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_festival_series_occurrence
        UNIQUE (festival_series_id, event_year, occurrence_sequence),
    CONSTRAINT uq_festival_provider_occurrence
        UNIQUE (provider, source_content_id, event_year, occurrence_sequence),
    CONSTRAINT fk_event_occurrence_series
        FOREIGN KEY (festival_series_id) REFERENCES festival_series (id),
    CONSTRAINT fk_event_occurrence_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT chk_event_occurrence_year
        CHECK (event_year BETWEEN 2000 AND 2100),
    CONSTRAINT chk_event_occurrence_sequence
        CHECK (occurrence_sequence >= 1),
    CONSTRAINT chk_event_occurrence_date_status
        CHECK (date_validation_status IN ('VALID', 'MISSING', 'INVALID')),
    CONSTRAINT chk_event_occurrence_valid_dates
        CHECK (
            date_validation_status <> 'VALID'
            OR (
                start_date IS NOT NULL
                AND end_date IS NOT NULL
                AND start_date <= end_date
                AND visible_from IS NOT NULL
                AND visible_from <= start_date
            )
        )
);

CREATE INDEX idx_event_occurrence_month
    ON place_event_occurrences (
        event_year,
        start_date,
        end_date,
        visible_from,
        place_id
    );

INSERT INTO service_regions
    (code, name_ko, name_en, display_order, map_asset_key)
VALUES
    ('SEOUL', '서울', 'Seoul', 1, 'seoul'),
    ('GYEONGGI', '경기', 'Gyeonggi and Incheon', 2, 'gyeonggi'),
    ('GANGWON', '강원', 'Gangwon', 3, 'gangwon'),
    ('CHUNGCHEONG', '충청', 'Chungcheong', 4, 'chungcheong'),
    ('JEOLLA', '전라', 'Jeolla', 5, 'jeolla'),
    ('GYEONGSANG', '경상', 'Gyeongsang', 6, 'gyeongsang'),
    ('JEJU', '제주', 'Jeju', 7, 'jeju');

INSERT INTO administrative_regions
    (provider, level, code, parent_code, name, service_region_code)
VALUES
    ('KTO', 'SIDO', '1', '', '서울', 'SEOUL'),
    ('KTO', 'SIDO', '2', '', '인천', 'GYEONGGI'),
    ('KTO', 'SIDO', '3', '', '대전', 'CHUNGCHEONG'),
    ('KTO', 'SIDO', '4', '', '대구', 'GYEONGSANG'),
    ('KTO', 'SIDO', '5', '', '광주', 'JEOLLA'),
    ('KTO', 'SIDO', '6', '', '부산', 'GYEONGSANG'),
    ('KTO', 'SIDO', '7', '', '울산', 'GYEONGSANG'),
    ('KTO', 'SIDO', '8', '', '세종', 'CHUNGCHEONG'),
    ('KTO', 'SIDO', '31', '', '경기', 'GYEONGGI'),
    ('KTO', 'SIDO', '32', '', '강원', 'GANGWON'),
    ('KTO', 'SIDO', '33', '', '충북', 'CHUNGCHEONG'),
    ('KTO', 'SIDO', '34', '', '충남', 'CHUNGCHEONG'),
    ('KTO', 'SIDO', '35', '', '경북', 'GYEONGSANG'),
    ('KTO', 'SIDO', '36', '', '경남', 'GYEONGSANG'),
    ('KTO', 'SIDO', '37', '', '전북', 'JEOLLA'),
    ('KTO', 'SIDO', '38', '', '전남', 'JEOLLA'),
    ('KTO', 'SIDO', '39', '', '제주', 'JEJU');
