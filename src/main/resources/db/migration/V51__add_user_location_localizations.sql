CREATE TABLE user_location_localizations (
    user_location_id BIGINT NOT NULL,
    language VARCHAR(2) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    road_address VARCHAR(500) NULL,
    address VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_location_id, language),
    CONSTRAINT fk_user_location_localizations_location
        FOREIGN KEY (user_location_id) REFERENCES user_locations(id) ON DELETE CASCADE,
    CONSTRAINT chk_user_location_localizations_language
        CHECK (language IN ('KO', 'EN'))
);

INSERT INTO user_location_localizations
    (user_location_id, language, display_name, road_address, address, created_at, updated_at)
SELECT id,
       CASE WHEN provider = 'GOOGLE_PLACES' THEN 'EN' ELSE 'KO' END,
       display_name, road_address, address, created_at, updated_at
FROM user_locations;
