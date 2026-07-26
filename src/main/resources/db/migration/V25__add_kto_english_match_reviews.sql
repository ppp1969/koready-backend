ALTER TABLE place_source_matches
    DROP CHECK chk_place_source_match_status;

ALTER TABLE place_source_matches
    ADD CONSTRAINT chk_place_source_match_status
        CHECK (
            status IN (
                'AUTO_CONFIRMED',
                'REVIEW_REQUIRED',
                'MANUAL_CONFIRMED',
                'REJECTED'
            )
        );

CREATE TABLE place_source_review_decisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_record_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    selected_place_id BIGINT NULL,
    reviewed_by VARCHAR(255) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    decided_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_place_source_review_decision UNIQUE (source_record_id),
    CONSTRAINT fk_place_source_review_decision_source
        FOREIGN KEY (source_record_id) REFERENCES place_source_records (id),
    CONSTRAINT fk_place_source_review_decision_place
        FOREIGN KEY (selected_place_id) REFERENCES places (id),
    CONSTRAINT chk_place_source_review_decision_status
        CHECK (status IN ('MANUAL_CONFIRMED', 'REJECTED')),
    CONSTRAINT chk_place_source_review_decision_selection
        CHECK (
            (status = 'MANUAL_CONFIRMED' AND selected_place_id IS NOT NULL)
            OR (status = 'REJECTED' AND selected_place_id IS NULL)
        ),
    CONSTRAINT chk_place_source_review_decision_version CHECK (version >= 1)
);

CREATE INDEX idx_place_source_review_decision_status
    ON place_source_review_decisions (status, source_record_id);

CREATE TABLE place_source_review_audits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_record_id BIGINT NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    previous_place_id BIGINT NULL,
    selected_place_id BIGINT NULL,
    reviewed_by VARCHAR(255) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    decision_version INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_place_source_review_audit_source
        FOREIGN KEY (source_record_id) REFERENCES place_source_records (id),
    CONSTRAINT fk_place_source_review_audit_previous_place
        FOREIGN KEY (previous_place_id) REFERENCES places (id),
    CONSTRAINT fk_place_source_review_audit_selected_place
        FOREIGN KEY (selected_place_id) REFERENCES places (id),
    CONSTRAINT chk_place_source_review_audit_status
        CHECK (
            (previous_status IS NULL
                OR previous_status IN ('MANUAL_CONFIRMED', 'REJECTED'))
            AND new_status IN ('MANUAL_CONFIRMED', 'REJECTED')
        ),
    CONSTRAINT chk_place_source_review_audit_version CHECK (decision_version >= 1)
);

CREATE INDEX idx_place_source_review_audit_source
    ON place_source_review_audits (source_record_id, id DESC);
