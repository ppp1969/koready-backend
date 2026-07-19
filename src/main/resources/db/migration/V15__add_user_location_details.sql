ALTER TABLE user_locations
    MODIFY COLUMN display_name VARCHAR(200) NOT NULL,
    ADD COLUMN custom_label VARCHAR(30) NULL AFTER display_name,
    ADD COLUMN provider VARCHAR(20) NULL AFTER custom_label,
    ADD COLUMN provider_place_id VARCHAR(191) NULL AFTER provider,
    ADD COLUMN road_address VARCHAR(500) NULL AFTER provider_place_id,
    ADD COLUMN address VARCHAR(500) NULL AFTER road_address,
    ADD COLUMN latitude DECIMAL(10, 7) NULL AFTER address,
    ADD COLUMN longitude DECIMAL(10, 7) NULL AFTER latitude,
    ADD COLUMN sido VARCHAR(100) NULL AFTER longitude,
    ADD COLUMN sigungu VARCHAR(100) NULL AFTER sido,
    ADD COLUMN dong VARCHAR(100) NULL AFTER sigungu;

ALTER TABLE user_locations
    ADD CONSTRAINT chk_user_location_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT chk_user_location_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180);
