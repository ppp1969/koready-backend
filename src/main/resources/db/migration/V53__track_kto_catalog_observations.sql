ALTER TABLE places
    ADD COLUMN kto_catalog_source_hash CHAR(64) NULL AFTER source_modified_time,
    ADD COLUMN kto_catalog_seen_at DATETIME(6) NULL AFTER kto_catalog_source_hash;

CREATE INDEX ix_places_kto_catalog_seen
    ON places (kto_catalog_seen_at, kto_content_id);

UPDATE places
SET kto_catalog_seen_at = updated_at
WHERE kto_content_id IS NOT NULL;
