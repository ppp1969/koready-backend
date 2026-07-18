CREATE TABLE user_saved_places (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    source VARCHAR(30) NOT NULL,
    saved_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_saved_places_user_place UNIQUE (user_id, place_id),
    CONSTRAINT fk_user_saved_places_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_saved_places_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT chk_user_saved_places_source CHECK (
        source IN ('HOME_MONTHLY', 'RECOMMENDATION_CARD', 'PLACE_DETAIL', 'MAP')
    ),
    INDEX idx_user_saved_places_active_list (
        user_id, deleted_at, saved_at DESC, id DESC
    )
);
