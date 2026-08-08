ALTER TABLE place_style_mappings
    ADD COLUMN rule_version VARCHAR(80) NULL AFTER source,
    ADD COLUMN evidence_json JSON NULL AFTER rule_version,
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE AFTER confidence,
    ADD COLUMN primary_place_id BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN is_primary = TRUE THEN place_id ELSE NULL END
        ) STORED AFTER is_primary,
    ADD CONSTRAINT chk_place_style_mapping_rule_version
        CHECK (rule_version IS NULL OR CHAR_LENGTH(TRIM(rule_version)) > 0),
    ADD CONSTRAINT chk_place_style_mapping_evidence
        CHECK (evidence_json IS NULL OR JSON_TYPE(evidence_json) = 'OBJECT');

UPDATE place_style_mappings target
JOIN (
    SELECT place_id, travel_style
    FROM (
        SELECT
            place_id,
            travel_style,
            ROW_NUMBER() OVER (
                PARTITION BY place_id
                ORDER BY
                    CASE WHEN source = 'MANUAL' THEN 0 ELSE 1 END,
                    confidence DESC,
                    travel_style ASC
            ) AS priority_order
        FROM place_style_mappings
    ) ranked
    WHERE ranked.priority_order = 1
) selected
  ON selected.place_id = target.place_id
 AND selected.travel_style = target.travel_style
SET target.is_primary = TRUE;

CREATE UNIQUE INDEX uq_place_style_mapping_primary
    ON place_style_mappings (primary_place_id);
