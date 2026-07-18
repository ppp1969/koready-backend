CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(100) NOT NULL,
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'KO',
    signup_status VARCHAR(30) NOT NULL DEFAULT 'NEED_TERMS',
    default_location_id BIGINT NULL,
    onboarding_completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_public_id UNIQUE (public_id),
    CONSTRAINT chk_users_language CHECK (preferred_language IN ('KO', 'EN')),
    CONSTRAINT chk_users_signup_status CHECK (
        signup_status IN ('NEED_TERMS', 'NEED_LANGUAGE', 'NEED_ONBOARDING', 'COMPLETED')
    )
);

CREATE TABLE user_locations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    service_region_code VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_location_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_location_region
        FOREIGN KEY (service_region_code) REFERENCES service_regions (code)
);

CREATE INDEX idx_user_locations_owner_active
    ON user_locations (user_id, deleted_at, id);

ALTER TABLE users
    ADD CONSTRAINT fk_users_default_location
    FOREIGN KEY (default_location_id) REFERENCES user_locations (id);

CREATE TABLE user_travel_styles (
    user_id BIGINT NOT NULL,
    travel_style VARCHAR(40) NOT NULL,
    display_order TINYINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, travel_style),
    CONSTRAINT uq_user_travel_style_order UNIQUE (user_id, display_order),
    CONSTRAINT fk_user_travel_style_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_user_travel_style_order CHECK (display_order BETWEEN 1 AND 4),
    CONSTRAINT chk_user_travel_style_value CHECK (
        travel_style IN (
            'LOCAL_FOOD',
            'LOCAL_FESTIVAL',
            'TRADITIONAL_MARKET',
            'CULTURE_EXPERIENCE',
            'NATURE',
            'EXHIBITION_MUSEUM',
            'DRAMA_LOCATION'
        )
    )
);

CREATE TABLE recommendation_decks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    scope VARCHAR(20) NOT NULL,
    origin_location_id BIGINT NOT NULL,
    origin_display_name VARCHAR(100) NOT NULL,
    origin_service_region_code VARCHAR(20) NOT NULL,
    language VARCHAR(10) NOT NULL,
    seed CHAR(64) NOT NULL,
    cursor_version SMALLINT NOT NULL,
    suppression_policy_version VARCHAR(100) NOT NULL,
    suppression_days SMALLINT NOT NULL,
    page_size SMALLINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_recommendation_deck_public_id UNIQUE (public_id),
    CONSTRAINT fk_recommendation_deck_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_recommendation_deck_origin
        FOREIGN KEY (origin_location_id) REFERENCES user_locations (id),
    CONSTRAINT fk_recommendation_deck_region
        FOREIGN KEY (origin_service_region_code) REFERENCES service_regions (code),
    CONSTRAINT chk_recommendation_deck_scope CHECK (scope IN ('NEARBY', 'NATIONWIDE')),
    CONSTRAINT chk_recommendation_deck_language CHECK (language IN ('KO', 'EN')),
    CONSTRAINT chk_recommendation_deck_page_size CHECK (page_size BETWEEN 1 AND 50),
    CONSTRAINT chk_recommendation_deck_policy CHECK (
        cursor_version >= 1
        AND suppression_days > 0
        AND expires_at > created_at
    )
);

CREATE INDEX idx_recommendation_decks_user_created
    ON recommendation_decks (user_id, created_at DESC, id DESC);

CREATE TABLE recommendation_deck_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    deck_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    display_order SMALLINT NOT NULL,
    title VARCHAR(300) NOT NULL,
    location_text VARCHAR(500) NOT NULL,
    image_url VARCHAR(1000) NULL,
    short_description VARCHAR(500) NULL,
    service_region_code VARCHAR(20) NOT NULL,
    travel_style VARCHAR(40) NULL,
    tags_json JSON NOT NULL,
    match_rank TINYINT NOT NULL,
    travel_style_matched BOOLEAN NOT NULL,
    preference_tag_matched BOOLEAN NOT NULL,
    matched_tag_codes_json JSON NOT NULL,
    served_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_recommendation_deck_item_place UNIQUE (deck_id, place_id),
    CONSTRAINT uq_recommendation_deck_item_order UNIQUE (deck_id, display_order),
    CONSTRAINT fk_recommendation_deck_item_deck
        FOREIGN KEY (deck_id) REFERENCES recommendation_decks (id),
    CONSTRAINT fk_recommendation_deck_item_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT fk_recommendation_deck_item_region
        FOREIGN KEY (service_region_code) REFERENCES service_regions (code),
    CONSTRAINT chk_recommendation_deck_item_order CHECK (display_order BETWEEN 1 AND 200),
    CONSTRAINT chk_recommendation_deck_item_rank CHECK (match_rank IN (1, 2, 3)),
    CONSTRAINT chk_recommendation_deck_item_tags CHECK (
        JSON_TYPE(tags_json) = 'ARRAY'
        AND JSON_TYPE(matched_tag_codes_json) = 'ARRAY'
    )
);

CREATE TABLE recommendation_deck_pages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    deck_id BIGINT NOT NULL,
    page_number SMALLINT NOT NULL,
    cursor_key VARCHAR(100) NOT NULL,
    start_order SMALLINT NOT NULL,
    end_order SMALLINT NOT NULL,
    served_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_recommendation_deck_page_number UNIQUE (deck_id, page_number),
    CONSTRAINT uq_recommendation_deck_page_cursor UNIQUE (cursor_key),
    CONSTRAINT fk_recommendation_deck_page_deck
        FOREIGN KEY (deck_id) REFERENCES recommendation_decks (id),
    CONSTRAINT chk_recommendation_deck_page_number CHECK (page_number >= 1),
    CONSTRAINT chk_recommendation_deck_page_range CHECK (
        start_order >= 1 AND end_order >= start_order - 1 AND end_order <= 200
    )
);

CREATE TABLE user_place_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    deck_id BIGINT NULL,
    policy_version VARCHAR(100) NULL,
    suppression_days SMALLINT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_user_place_event_public_id UNIQUE (public_id),
    CONSTRAINT fk_user_place_event_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_place_event_place FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT fk_user_place_event_deck
        FOREIGN KEY (deck_id) REFERENCES recommendation_decks (id),
    CONSTRAINT chk_user_place_event_type CHECK (
        event_type IN (
            'CARD_SERVED',
            'CARD_EXPANDED',
            'CARD_PREVIOUS',
            'CARD_NEXT',
            'PLACE_DETAIL_CLICKED',
            'PLACE_SAVED',
            'PLACE_UNSAVED',
            'ROUTE_OPENED'
        )
    ),
    CONSTRAINT chk_user_place_event_suppression CHECK (
        (event_type = 'CARD_SERVED' AND policy_version IS NOT NULL AND suppression_days > 0)
        OR (event_type <> 'CARD_SERVED')
    )
);

CREATE INDEX idx_user_place_events_user_time
    ON user_place_events (user_id, occurred_at DESC, id DESC);
CREATE INDEX idx_user_place_events_deck
    ON user_place_events (deck_id, id);

CREATE TABLE user_place_recommendation_states (
    user_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    first_served_at DATETIME(6) NOT NULL,
    last_served_at DATETIME(6) NOT NULL,
    served_count INT NOT NULL,
    last_deck_id BIGINT NOT NULL,
    suppress_until DATETIME(6) NOT NULL,
    suppression_policy_version VARCHAR(100) NOT NULL,
    last_suppression_days SMALLINT NOT NULL,
    last_event_type VARCHAR(40) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, place_id),
    CONSTRAINT fk_recommendation_state_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_recommendation_state_place FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT fk_recommendation_state_deck
        FOREIGN KEY (last_deck_id) REFERENCES recommendation_decks (id),
    CONSTRAINT chk_recommendation_state_count CHECK (served_count >= 1),
    CONSTRAINT chk_recommendation_state_window CHECK (suppress_until > last_served_at),
    CONSTRAINT chk_recommendation_state_policy CHECK (last_suppression_days > 0),
    CONSTRAINT chk_recommendation_state_event CHECK (last_event_type = 'CARD_SERVED')
);

CREATE INDEX idx_recommendation_states_suppression
    ON user_place_recommendation_states (user_id, suppress_until, place_id);
