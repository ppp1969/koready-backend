ALTER TABLE term_versions
    DROP CHECK ck_term_versions_withdrawal,
    ADD CONSTRAINT ck_term_versions_withdrawal CHECK (
        withdrawn_at IS NULL
        OR (published_at IS NOT NULL AND withdrawn_at >= published_at)
    );
