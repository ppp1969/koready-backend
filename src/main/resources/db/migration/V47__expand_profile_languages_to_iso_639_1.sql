ALTER TABLE buddy_profile_languages
    DROP CHECK chk_buddy_profile_language;

ALTER TABLE buddy_profile_languages
    ADD CONSTRAINT chk_buddy_profile_language CHECK (
        language_code REGEXP '^[A-Z]{2}$'
    );
