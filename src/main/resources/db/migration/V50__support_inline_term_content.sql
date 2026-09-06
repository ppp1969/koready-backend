ALTER TABLE term_versions
    ADD COLUMN content_body LONGTEXT NULL AFTER content_url,
    ADD COLUMN content_format VARCHAR(20) NULL AFTER content_body;

ALTER TABLE term_versions
    DROP CHECK ck_term_versions_publishable;

ALTER TABLE term_versions
    ADD CONSTRAINT ck_term_versions_content_source
        CHECK (
            (content_url IS NULL AND content_body IS NULL AND content_format IS NULL)
            OR (content_url IS NOT NULL AND content_body IS NULL AND content_format IS NULL)
            OR (content_url IS NULL AND content_body IS NOT NULL
                AND content_format IN ('PLAIN_TEXT', 'MARKDOWN'))
        ),
    ADD CONSTRAINT ck_term_versions_publishable
        CHECK (
            published_at IS NULL
            OR content_url IS NOT NULL
            OR (content_body IS NOT NULL AND content_format IS NOT NULL)
        );
