CREATE TABLE term_definitions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    display_order INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_term_definitions_code UNIQUE (code),
    CONSTRAINT ck_term_definitions_display_order CHECK (display_order > 0)
);

CREATE TABLE term_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    term_id BIGINT NOT NULL,
    version_label VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content_url VARCHAR(2048) NULL,
    required BOOLEAN NOT NULL,
    effective_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    withdrawn_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_term_versions_term_version
        UNIQUE (term_id, version_label),
    CONSTRAINT fk_term_versions_definition
        FOREIGN KEY (term_id) REFERENCES term_definitions (id),
    CONSTRAINT ck_term_versions_publishable
        CHECK (published_at IS NULL OR content_url IS NOT NULL),
    CONSTRAINT ck_term_versions_withdrawal
        CHECK (withdrawn_at IS NULL OR withdrawn_at > effective_at)
);

CREATE INDEX idx_term_versions_current
    ON term_versions (
        term_id,
        published_at,
        effective_at,
        withdrawn_at
    );

CREATE TABLE user_term_agreements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    term_version_id BIGINT NOT NULL,
    agreed BOOLEAN NOT NULL,
    agreed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_term_agreements_user_version
        UNIQUE (user_id, term_version_id),
    CONSTRAINT fk_user_term_agreements_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_term_agreements_version
        FOREIGN KEY (term_version_id) REFERENCES term_versions (id),
    CONSTRAINT ck_user_term_agreements_timestamp
        CHECK (
            (agreed = TRUE AND agreed_at IS NOT NULL)
            OR (agreed = FALSE AND agreed_at IS NULL)
        )
);

CREATE INDEX idx_user_term_agreements_version
    ON user_term_agreements (term_version_id, user_id);
