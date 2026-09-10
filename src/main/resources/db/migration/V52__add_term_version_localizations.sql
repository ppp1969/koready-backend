CREATE TABLE term_version_localizations (
    term_version_id BIGINT NOT NULL,
    language VARCHAR(2) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content_url VARCHAR(2048) NULL,
    content_body LONGTEXT NULL,
    content_format VARCHAR(20) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (term_version_id, language),
    CONSTRAINT fk_term_version_localizations_version
        FOREIGN KEY (term_version_id) REFERENCES term_versions(id) ON DELETE CASCADE,
    CONSTRAINT chk_term_version_localizations_language CHECK (language IN ('KO', 'EN')),
    CONSTRAINT chk_term_version_localizations_content_source CHECK (
        (content_url IS NOT NULL AND content_body IS NULL AND content_format IS NULL)
        OR (content_url IS NULL AND content_body IS NOT NULL
            AND content_format IN ('PLAIN_TEXT', 'MARKDOWN'))
    )
);

INSERT INTO term_version_localizations
    (term_version_id, language, title, content_url, content_body, content_format, created_at, updated_at)
SELECT id, 'KO', title, content_url, content_body, content_format, created_at, updated_at
FROM term_versions
WHERE content_url IS NOT NULL OR content_body IS NOT NULL;
