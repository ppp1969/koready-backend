CREATE TABLE buddy_blocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    blocker_user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_buddy_block_users UNIQUE (blocker_user_id, blocked_user_id),
    CONSTRAINT fk_buddy_block_blocker
        FOREIGN KEY (blocker_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_buddy_block_blocked
        FOREIGN KEY (blocked_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_buddy_block_not_self CHECK (blocker_user_id <> blocked_user_id)
);

CREATE INDEX idx_buddy_blocks_blocker_created
    ON buddy_blocks (blocker_user_id, created_at DESC, id DESC);

CREATE INDEX idx_buddy_blocks_blocked_blocker
    ON buddy_blocks (blocked_user_id, blocker_user_id);
