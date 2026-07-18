CREATE TABLE place_style_mappings (
    place_id BIGINT NOT NULL,
    travel_style VARCHAR(40) NOT NULL,
    source VARCHAR(30) NOT NULL,
    confidence DECIMAL(5, 4) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (place_id, travel_style),
    CONSTRAINT fk_place_style_mapping_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT chk_place_style_mapping_style
        CHECK (
            travel_style IN (
                'LOCAL_FOOD',
                'LOCAL_FESTIVAL',
                'TRADITIONAL_MARKET',
                'CULTURE_EXPERIENCE',
                'NATURE',
                'EXHIBITION_MUSEUM',
                'DRAMA_LOCATION'
            )
        ),
    CONSTRAINT chk_place_style_mapping_source
        CHECK (source IN ('CONTENT_TYPE', 'LCLS', 'KEYWORD', 'AI', 'MANUAL')),
    CONSTRAINT chk_place_style_mapping_confidence
        CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE INDEX idx_place_style_mapping_filter
    ON place_style_mappings (travel_style, confidence, place_id);
