CREATE TABLE place_editorial_source_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    source_snapshot_json JSON NOT NULL,
    reviewed_by_subject VARCHAR(255) NOT NULL,
    reviewed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_editorial_source_review_place UNIQUE (place_id),
    CONSTRAINT fk_editorial_source_review_place
        FOREIGN KEY (place_id) REFERENCES places (id)
);

CREATE INDEX idx_editorial_source_review_fingerprint
    ON place_editorial_source_reviews (source_fingerprint);
