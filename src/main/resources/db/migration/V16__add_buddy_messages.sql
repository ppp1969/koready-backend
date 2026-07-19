CREATE TABLE buddy_message_threads (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    place_id BIGINT NOT NULL,
    profile_low_id BIGINT NOT NULL,
    profile_high_id BIGINT NOT NULL,
    last_message_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_buddy_message_thread_public_id UNIQUE (public_id),
    CONSTRAINT uq_buddy_message_thread_participants
        UNIQUE (place_id, profile_low_id, profile_high_id),
    CONSTRAINT fk_buddy_message_thread_place
        FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT fk_buddy_message_thread_low_profile
        FOREIGN KEY (profile_low_id) REFERENCES buddy_profiles (id),
    CONSTRAINT fk_buddy_message_thread_high_profile
        FOREIGN KEY (profile_high_id) REFERENCES buddy_profiles (id),
    CONSTRAINT chk_buddy_message_thread_profile_order
        CHECK (profile_low_id < profile_high_id)
);

CREATE INDEX idx_buddy_message_threads_low_updated
    ON buddy_message_threads (profile_low_id, updated_at DESC, id DESC);
CREATE INDEX idx_buddy_message_threads_high_updated
    ON buddy_message_threads (profile_high_id, updated_at DESC, id DESC);

CREATE TABLE buddy_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    thread_id BIGINT NOT NULL,
    sender_profile_id BIGINT NOT NULL,
    receiver_profile_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    deleted_by_sender_at DATETIME(6) NULL,
    deleted_by_receiver_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_buddy_message_thread
        FOREIGN KEY (thread_id) REFERENCES buddy_message_threads (id),
    CONSTRAINT fk_buddy_message_sender
        FOREIGN KEY (sender_profile_id) REFERENCES buddy_profiles (id),
    CONSTRAINT fk_buddy_message_receiver
        FOREIGN KEY (receiver_profile_id) REFERENCES buddy_profiles (id),
    CONSTRAINT chk_buddy_message_distinct_participants
        CHECK (sender_profile_id <> receiver_profile_id),
    CONSTRAINT chk_buddy_message_content
        CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 1000)
);

CREATE INDEX idx_buddy_messages_thread_page
    ON buddy_messages (thread_id, id DESC);
CREATE INDEX idx_buddy_messages_receiver_unread
    ON buddy_messages (receiver_profile_id, read_at, id DESC);

ALTER TABLE buddy_message_threads
    ADD CONSTRAINT fk_buddy_message_thread_last_message
    FOREIGN KEY (last_message_id) REFERENCES buddy_messages (id);

CREATE TABLE message_idempotency_keys (
    sender_profile_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    message_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (sender_profile_id, idempotency_key),
    CONSTRAINT uq_message_idempotency_message UNIQUE (message_id),
    CONSTRAINT fk_message_idempotency_sender
        FOREIGN KEY (sender_profile_id) REFERENCES buddy_profiles (id),
    CONSTRAINT fk_message_idempotency_message
        FOREIGN KEY (message_id) REFERENCES buddy_messages (id),
    CONSTRAINT chk_message_idempotency_key_length
        CHECK (CHAR_LENGTH(idempotency_key) BETWEEN 8 AND 100),
    CONSTRAINT chk_message_idempotency_hash_length
        CHECK (CHAR_LENGTH(request_hash) = 64)
);
