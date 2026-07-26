CREATE TABLE place_detail_attributes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    source_operation VARCHAR(100) NOT NULL,
    item_sequence INT NOT NULL,
    field_code VARCHAR(100) NOT NULL,
    value_text TEXT NOT NULL,
    source_content_id VARCHAR(100) NOT NULL,
    source_snapshot_id BIGINT NOT NULL,
    source_hash CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_place_detail_attribute
        UNIQUE (place_id, source_operation, item_sequence, field_code),
    CONSTRAINT fk_place_detail_attribute_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT fk_place_detail_attribute_snapshot
        FOREIGN KEY (source_snapshot_id) REFERENCES open_api_raw_snapshots (id),
    CONSTRAINT chk_place_detail_attribute_operation
        CHECK (source_operation IN ('detailIntro2', 'detailInfo2')),
    CONSTRAINT chk_place_detail_attribute_sequence
        CHECK (item_sequence >= 1)
);

CREATE INDEX idx_place_detail_attribute_lookup
    ON place_detail_attributes (place_id, field_code, item_sequence);
