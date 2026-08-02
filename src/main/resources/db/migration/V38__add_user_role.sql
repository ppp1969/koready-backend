ALTER TABLE users
    ADD COLUMN role VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL DEFAULT 'USER' AFTER public_id,
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN', 'OPERATOR', 'AUDITOR'));
