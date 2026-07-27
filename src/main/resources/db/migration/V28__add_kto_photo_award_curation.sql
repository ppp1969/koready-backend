CREATE TABLE kto_photo_awards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_id VARCHAR(100) NOT NULL,
    title_ko VARCHAR(500) NOT NULL,
    film_location_ko VARCHAR(500) NULL,
    keyword_ko VARCHAR(1000) NULL,
    title_en VARCHAR(500) NULL,
    film_location_en VARCHAR(500) NULL,
    keyword_en VARCHAR(1000) NULL,
    original_image_url VARCHAR(1000) NOT NULL,
    thumbnail_image_url VARCHAR(1000) NULL,
    copyright_type VARCHAR(30) NULL,
    source_hash CHAR(64) NOT NULL,
    raw_snapshot_id BIGINT NOT NULL,
    source_captured_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_kto_photo_award_content UNIQUE (content_id),
    CONSTRAINT fk_kto_photo_award_snapshot
        FOREIGN KEY (raw_snapshot_id) REFERENCES open_api_raw_snapshots (id)
);

CREATE INDEX idx_kto_photo_award_title
    ON kto_photo_awards (title_ko, id);

CREATE TABLE kto_photo_award_place_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    photo_award_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    display_order SMALLINT NOT NULL,
    approved_by_subject VARCHAR(191) NOT NULL,
    approval_reason VARCHAR(500) NOT NULL,
    approved_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_kto_photo_award_mapping_award UNIQUE (photo_award_id),
    CONSTRAINT uq_kto_photo_award_mapping_order
        UNIQUE (place_id, display_order),
    CONSTRAINT fk_kto_photo_award_mapping_award
        FOREIGN KEY (photo_award_id) REFERENCES kto_photo_awards (id),
    CONSTRAINT fk_kto_photo_award_mapping_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT chk_kto_photo_award_mapping_order
        CHECK (display_order BETWEEN 1 AND 20)
);

CREATE INDEX idx_kto_photo_award_mapping_place
    ON kto_photo_award_place_mappings (place_id, display_order);
