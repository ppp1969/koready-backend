CREATE TABLE user_social_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    email VARCHAR(320) NOT NULL,
    email_verified BOOLEAN NOT NULL,
    last_login_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_social_identity_subject
        UNIQUE (provider, provider_subject),
    CONSTRAINT uq_user_social_identity_user_provider
        UNIQUE (user_id, provider),
    CONSTRAINT fk_user_social_identity_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_social_identity_provider
        CHECK (provider = 'GOOGLE'),
    CONSTRAINT chk_user_social_identity_verified
        CHECK (email_verified = TRUE)
);

CREATE INDEX idx_user_social_identity_user
    ON user_social_identities (user_id, provider);

CREATE TABLE auth_refresh_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    device_id_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_auth_refresh_session_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_refresh_session_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_auth_refresh_session_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT chk_auth_refresh_session_revocation
        CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX idx_auth_refresh_session_user_active
    ON auth_refresh_sessions (user_id, revoked_at, expires_at, id);
