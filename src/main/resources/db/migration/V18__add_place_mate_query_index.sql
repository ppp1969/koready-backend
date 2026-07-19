CREATE INDEX idx_user_saved_places_place_mates
    ON user_saved_places (place_id, deleted_at, saved_at DESC, id DESC);
