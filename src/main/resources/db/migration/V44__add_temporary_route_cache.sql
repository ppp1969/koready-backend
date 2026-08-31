CREATE TABLE route_caches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(80) NOT NULL,
    user_id BIGINT NOT NULL,
    destination_place_id BIGINT NOT NULL,
    normalized_route_json JSON NOT NULL,
    fetched_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_route_cache_public_id UNIQUE (public_id),
    CONSTRAINT fk_route_cache_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_route_cache_destination
        FOREIGN KEY (destination_place_id) REFERENCES places (id),
    CONSTRAINT chk_route_cache_expiration CHECK (expires_at > fetched_at)
);

CREATE INDEX idx_route_cache_owner_expiration
    ON route_caches (user_id, public_id, expires_at);
CREATE INDEX idx_route_cache_expiration ON route_caches (expires_at);
