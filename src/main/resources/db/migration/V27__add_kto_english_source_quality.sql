ALTER TABLE place_source_records
    ADD COLUMN source_quality VARCHAR(30) NULL AFTER source_hash,
    ADD COLUMN quality_warnings JSON NULL AFTER source_quality,
    ADD COLUMN quality_classified_at DATETIME(6) NULL AFTER quality_warnings,
    ADD COLUMN quality_classifier_version VARCHAR(50) NULL AFTER quality_classified_at,
    ADD CONSTRAINT chk_place_source_record_quality
        CHECK (
            source_quality IS NULL
            OR source_quality IN (
                'USABLE',
                'NON_ENGLISH_SUSPECTED',
                'ENCODING_SUSPECTED',
                'MIXED_OR_UNKNOWN'
            )
        );

CREATE INDEX idx_place_source_quality_review
    ON place_source_records
        (provider, api_name, language, source_quality, id DESC);
