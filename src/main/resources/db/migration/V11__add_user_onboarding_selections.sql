CREATE TABLE user_onboarding_place_selections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    candidate_set_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    selected_order TINYINT NOT NULL,
    selected_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_onboarding_selection_order
        UNIQUE (user_id, selected_order),
    CONSTRAINT uq_user_onboarding_selection_place
        UNIQUE (user_id, place_id),
    CONSTRAINT fk_user_onboarding_selection_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_onboarding_selection_candidate_item
        FOREIGN KEY (candidate_set_id, place_id)
        REFERENCES onboarding_candidate_set_items (candidate_set_id, place_id),
    CONSTRAINT chk_user_onboarding_selection_order
        CHECK (selected_order BETWEEN 1 AND 3)
);

CREATE INDEX idx_user_onboarding_selection_candidate
    ON user_onboarding_place_selections (candidate_set_id, place_id);
