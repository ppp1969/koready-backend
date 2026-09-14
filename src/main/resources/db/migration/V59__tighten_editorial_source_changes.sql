SET @add_jobs_source_snapshot = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'place_editorial_jobs'
          AND COLUMN_NAME = 'source_snapshot_json'
    ),
    'SELECT 1',
    'ALTER TABLE place_editorial_jobs ADD COLUMN source_snapshot_json JSON NULL AFTER source_fingerprint'
);
PREPARE add_jobs_source_snapshot_stmt FROM @add_jobs_source_snapshot;
EXECUTE add_jobs_source_snapshot_stmt;
DEALLOCATE PREPARE add_jobs_source_snapshot_stmt;

SET @add_contents_source_snapshot = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'place_editorial_contents'
          AND COLUMN_NAME = 'source_snapshot_json'
    ),
    'SELECT 1',
    'ALTER TABLE place_editorial_contents ADD COLUMN source_snapshot_json JSON NULL AFTER source_fingerprint'
);
PREPARE add_contents_source_snapshot_stmt FROM @add_contents_source_snapshot;
EXECUTE add_contents_source_snapshot_stmt;
DEALLOCATE PREPARE add_contents_source_snapshot_stmt;

SET SESSION group_concat_max_len = 16777216;

CREATE TEMPORARY TABLE editorial_source_baseline_v59 (
    PRIMARY KEY (place_id)
) AS
WITH ranked_facts AS (
    SELECT
        place_id,
        field_code,
        TRIM(REGEXP_REPLACE(
            REGEXP_REPLACE(REPLACE(value_text, '&nbsp;', ' '), '<[^>]*>', ' '),
            '[[:space:]]+', ' '
        )) AS normalized_value,
        ROW_NUMBER() OVER (
            PARTITION BY place_id
            ORDER BY source_operation, item_sequence, field_code, id
        ) AS fact_order
    FROM place_detail_attributes
    WHERE NULLIF(TRIM(value_text), '') IS NOT NULL
), facts AS (
    SELECT
        place_id,
        GROUP_CONCAT(
            CONCAT(field_code, ': ', normalized_value)
            ORDER BY fact_order SEPARATOR '\n'
        ) AS facts_text
    FROM ranked_facts
    WHERE fact_order <= 30
    GROUP BY place_id
), styles AS (
    SELECT
        place_id,
        GROUP_CONCAT(
            travel_style
            ORDER BY is_primary DESC, confidence DESC, travel_style
            SEPARATOR ','
        ) AS travel_styles
    FROM place_style_mappings
    GROUP BY place_id
)
SELECT
    p.id AS place_id,
    TRIM(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(COALESCE(ko.title, ''), '&nbsp;', ' '), '<[^>]*>', ' '), '[[:space:]]+', ' ')) AS title_ko,
    TRIM(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(COALESCE(en.title, ''), '&nbsp;', ' '), '<[^>]*>', ' '), '[[:space:]]+', ' ')) AS title_en,
    TRIM(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(COALESCE(ko.address_text, p.road_address, p.address, ''), '&nbsp;', ' '), '<[^>]*>', ' '), '[[:space:]]+', ' ')) AS address_text,
    TRIM(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(COALESCE(ko.overview, ''), '&nbsp;', ' '), '<[^>]*>', ' '), '[[:space:]]+', ' ')) AS overview_ko,
    COALESCE(styles.travel_styles, '') AS travel_styles,
    COALESCE(facts.facts_text, '') AS facts_text
FROM places p
LEFT JOIN place_localizations ko
  ON ko.place_id = p.id AND ko.language = 'KO'
LEFT JOIN place_localizations en
  ON en.place_id = p.id AND en.language = 'EN'
 AND en.translation_source IN ('KTO_EN', 'MANUAL_EDITED')
LEFT JOIN styles ON styles.place_id = p.id
LEFT JOIN facts ON facts.place_id = p.id;

UPDATE place_editorial_contents content
JOIN (
    SELECT place_id, prompt_version, MAX(id) AS content_id
    FROM place_editorial_contents
    WHERE status = 'READY'
    GROUP BY place_id, prompt_version
) latest ON latest.content_id = content.id
JOIN editorial_source_baseline_v59 source ON source.place_id = content.place_id
SET content.source_fingerprint = SHA2(CONCAT_WS('|',
        source.title_ko, source.title_en, source.address_text,
        source.overview_ko, source.travel_styles, source.facts_text
    ), 256),
    content.source_snapshot_json = JSON_OBJECT(
        'titleKo', source.title_ko,
        'titleEn', source.title_en,
        'address', source.address_text,
        'overviewKo', source.overview_ko,
        'travelStyles', source.travel_styles,
        'facts', source.facts_text
    );

DROP TEMPORARY TABLE editorial_source_baseline_v59;
