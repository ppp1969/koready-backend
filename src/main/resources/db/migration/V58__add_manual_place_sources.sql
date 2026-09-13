CREATE TABLE manual_place_sources (
    place_id BIGINT NOT NULL,
    source_url VARCHAR(1000) NULL,
    source_note VARCHAR(2000) NULL,
    created_by_subject VARCHAR(191) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (place_id),
    CONSTRAINT fk_manual_place_sources_place
        FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT chk_manual_place_sources_evidence
        CHECK (
            NULLIF(TRIM(source_url), '') IS NOT NULL
            OR NULLIF(TRIM(source_note), '') IS NOT NULL
        )
);
