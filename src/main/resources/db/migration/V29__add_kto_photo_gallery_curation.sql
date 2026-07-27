ALTER TABLE place_images
    DROP CHECK chk_place_image_source_type;

ALTER TABLE place_images
    ADD CONSTRAINT chk_place_image_source_type
        CHECK (
            source_type IN (
                'KTO_DETAIL',
                'KTO_PHOTO_AWARD',
                'KTO_PHOTO_GALLERY',
                'MANUAL',
                'S3'
            )
        );

CREATE TABLE kto_photo_gallery_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_id VARCHAR(100) NOT NULL,
    content_type_id VARCHAR(30) NULL,
    title VARCHAR(500) NOT NULL,
    photography_location VARCHAR(500) NULL,
    photography_month VARCHAR(30) NULL,
    photographer VARCHAR(300) NULL,
    search_keyword VARCHAR(1000) NULL,
    image_url VARCHAR(1000) NOT NULL,
    rights_status VARCHAR(30) NOT NULL DEFAULT 'REQUIRES_REVIEW',
    source_created_time VARCHAR(30) NULL,
    source_modified_time VARCHAR(30) NULL,
    source_hash CHAR(64) NOT NULL,
    raw_snapshot_id BIGINT NOT NULL,
    source_captured_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_kto_photo_gallery_content UNIQUE (content_id),
    CONSTRAINT fk_kto_photo_gallery_snapshot
        FOREIGN KEY (raw_snapshot_id) REFERENCES open_api_raw_snapshots (id),
    CONSTRAINT chk_kto_photo_gallery_rights
        CHECK (rights_status IN ('REQUIRES_REVIEW'))
);

CREATE INDEX idx_kto_photo_gallery_title
    ON kto_photo_gallery_images (title, id);

CREATE TABLE kto_photo_gallery_place_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    photo_gallery_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    display_order SMALLINT NOT NULL,
    approved_by_subject VARCHAR(191) NOT NULL,
    approval_reason VARCHAR(500) NOT NULL,
    approved_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_kto_photo_gallery_mapping_image
        UNIQUE (photo_gallery_id),
    CONSTRAINT uq_kto_photo_gallery_mapping_order
        UNIQUE (place_id, display_order),
    CONSTRAINT fk_kto_photo_gallery_mapping_image
        FOREIGN KEY (photo_gallery_id)
        REFERENCES kto_photo_gallery_images (id),
    CONSTRAINT fk_kto_photo_gallery_mapping_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT chk_kto_photo_gallery_mapping_order
        CHECK (display_order BETWEEN 1 AND 20)
);

CREATE INDEX idx_kto_photo_gallery_mapping_place
    ON kto_photo_gallery_place_mappings (place_id, display_order);
