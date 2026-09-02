ALTER TABLE users
    ADD COLUMN account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' AFTER role,
    ADD CONSTRAINT chk_users_account_status CHECK (
        account_status IN ('ACTIVE', 'WITHDRAWAL_PENDING', 'WITHDRAWN')
    );

CREATE INDEX idx_users_account_status ON users (account_status, id);

CREATE TABLE account_withdrawals (
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    scheduled_for DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    message_purge_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    previous_profile_public BOOLEAN NULL,
    previous_sns_public BOOLEAN NULL,
    previous_allows_messages BOOLEAN NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_account_withdrawal_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_account_withdrawal_status CHECK (
        status IN ('PENDING', 'CANCELLED', 'CONFIRMED')
    ),
    CONSTRAINT chk_account_withdrawal_schedule CHECK (scheduled_for > requested_at)
);

CREATE INDEX idx_account_withdrawal_confirmation
    ON account_withdrawals (status, scheduled_for, user_id);
CREATE INDEX idx_account_withdrawal_message_purge
    ON account_withdrawals (status, message_purge_at, user_id);
