CREATE TABLE kto_related_tour_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    base_ym CHAR(6) NOT NULL,
    area_code VARCHAR(30) NOT NULL,
    area_name VARCHAR(100) NULL,
    signgu_code VARCHAR(30) NOT NULL,
    signgu_name VARCHAR(100) NULL,
    source_tour_code CHAR(32) NOT NULL,
    source_name VARCHAR(300) NOT NULL,
    related_tour_code CHAR(32) NOT NULL,
    related_name VARCHAR(300) NOT NULL,
    related_region_code VARCHAR(30) NULL,
    related_region_name VARCHAR(100) NULL,
    related_signgu_code VARCHAR(30) NULL,
    related_signgu_name VARCHAR(100) NULL,
    category_large VARCHAR(100) NULL,
    category_medium VARCHAR(100) NULL,
    category_small VARCHAR(100) NULL,
    related_rank SMALLINT NOT NULL,
    source_hash CHAR(64) NOT NULL,
    raw_snapshot_id BIGINT NOT NULL,
    source_captured_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_kto_related_tour_pair
        UNIQUE (base_ym, source_tour_code, related_tour_code),
    CONSTRAINT fk_kto_related_tour_snapshot
        FOREIGN KEY (raw_snapshot_id) REFERENCES open_api_raw_snapshots (id),
    CONSTRAINT chk_kto_related_tour_rank
        CHECK (related_rank BETWEEN 1 AND 50)
);

CREATE INDEX idx_kto_related_tour_region
    ON kto_related_tour_records
        (base_ym, area_code, signgu_code, id);
CREATE INDEX idx_kto_related_tour_names
    ON kto_related_tour_records
        (source_name, related_name, id);

CREATE TABLE kto_related_tour_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    related_tour_record_id BIGINT NOT NULL,
    source_place_id BIGINT NOT NULL,
    related_place_id BIGINT NOT NULL,
    match_status VARCHAR(30) NOT NULL,
    match_evidence JSON NULL,
    confirmed_by_subject VARCHAR(191) NULL,
    confirmation_reason VARCHAR(500) NULL,
    confirmed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_kto_related_tour_mapping_record
        UNIQUE (related_tour_record_id),
    CONSTRAINT fk_kto_related_tour_mapping_record
        FOREIGN KEY (related_tour_record_id)
        REFERENCES kto_related_tour_records (id),
    CONSTRAINT fk_kto_related_tour_mapping_source_place
        FOREIGN KEY (source_place_id) REFERENCES places (id),
    CONSTRAINT fk_kto_related_tour_mapping_related_place
        FOREIGN KEY (related_place_id) REFERENCES places (id),
    CONSTRAINT chk_kto_related_tour_mapping_status
        CHECK (match_status IN ('AUTO_CONFIRMED', 'MANUAL_CONFIRMED')),
    CONSTRAINT chk_kto_related_tour_distinct_places
        CHECK (source_place_id <> related_place_id)
);

CREATE INDEX idx_kto_related_tour_mapping_source
    ON kto_related_tour_mappings
        (source_place_id, match_status, related_tour_record_id);
CREATE INDEX idx_kto_related_tour_mapping_related
    ON kto_related_tour_mappings (related_place_id);

CREATE TABLE place_relations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    related_tour_record_id BIGINT NOT NULL,
    source_place_id BIGINT NOT NULL,
    related_place_id BIGINT NOT NULL,
    relation_source VARCHAR(30) NOT NULL,
    relation_type VARCHAR(100) NULL,
    relation_rank SMALLINT NOT NULL,
    source_base_ym CHAR(6) NOT NULL,
    last_synced_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_place_relation_record
        UNIQUE (related_tour_record_id),
    CONSTRAINT fk_place_relation_record
        FOREIGN KEY (related_tour_record_id)
        REFERENCES kto_related_tour_records (id),
    CONSTRAINT fk_place_relation_source_place
        FOREIGN KEY (source_place_id) REFERENCES places (id),
    CONSTRAINT fk_place_relation_related_place
        FOREIGN KEY (related_place_id) REFERENCES places (id),
    CONSTRAINT chk_place_relation_source
        CHECK (relation_source = 'KTO_RELATED'),
    CONSTRAINT chk_place_relation_rank
        CHECK (relation_rank BETWEEN 1 AND 50),
    CONSTRAINT chk_place_relation_distinct_places
        CHECK (source_place_id <> related_place_id)
);

CREATE INDEX idx_place_relations_source_rank
    ON place_relations
        (source_place_id, relation_source, relation_rank, id);
