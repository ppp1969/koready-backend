ALTER TABLE places
    ADD COLUMN curation_priority SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE places
    ADD CONSTRAINT chk_places_curation_priority
        CHECK (curation_priority BETWEEN 0 AND 1000);

CREATE INDEX idx_places_public_curation
    ON places (active, show_flag, curation_priority DESC, id DESC);

ALTER TABLE place_images
    ADD COLUMN admin_display_order SMALLINT NULL;

ALTER TABLE place_images
    ADD CONSTRAINT chk_place_images_admin_display_order
        CHECK (admin_display_order IS NULL OR admin_display_order BETWEEN 1 AND 100);

CREATE UNIQUE INDEX uq_place_images_admin_display_order
    ON place_images (place_id, admin_display_order);
