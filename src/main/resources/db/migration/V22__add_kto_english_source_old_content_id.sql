ALTER TABLE place_source_records
    ADD COLUMN source_old_content_id VARCHAR(100) NULL
        AFTER source_content_id;

CREATE INDEX idx_place_source_old_content_id
    ON place_source_records (provider, language, source_old_content_id);
