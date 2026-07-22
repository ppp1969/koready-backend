CREATE TABLE place_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    thumbnail_image_url VARCHAR(1000) NULL,
    image_url_sha256 CHAR(64) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_priority SMALLINT NOT NULL,
    source_order SMALLINT NOT NULL,
    source_content_id VARCHAR(100) NULL,
    source_image_name VARCHAR(500) NULL,
    copyright_type VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_place_image_source UNIQUE (place_id, source_type, image_url_sha256),
    CONSTRAINT fk_place_image_place FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT chk_place_image_source_type
        CHECK (source_type IN ('KTO_DETAIL', 'KTO_PHOTO_AWARD', 'MANUAL', 'S3')),
    CONSTRAINT chk_place_image_priority CHECK (source_priority >= 0),
    CONSTRAINT chk_place_image_order CHECK (source_order >= 1)
);

CREATE INDEX idx_place_images_gallery_order
    ON place_images (place_id, source_priority DESC, source_order ASC, id ASC);
