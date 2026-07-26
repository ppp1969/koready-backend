ALTER TABLE buddy_profile_languages
    DROP CHECK chk_buddy_profile_language,
    DROP CHECK chk_buddy_profile_language_order;

ALTER TABLE buddy_profile_languages
    ADD CONSTRAINT chk_buddy_profile_language CHECK (
        language_code IN (
            'KO', 'EN', 'ZH', 'JA', 'VI', 'TH', 'MN',
            'RU', 'ID', 'ES', 'FR', 'DE', 'AR'
        )
    ),
    ADD CONSTRAINT chk_buddy_profile_language_order CHECK (
        display_order BETWEEN 1 AND 5
    );

DELETE duplicate_link
FROM buddy_social_links duplicate_link
JOIN buddy_social_links kept_link
  ON kept_link.profile_id = duplicate_link.profile_id
 AND kept_link.link_type = duplicate_link.link_type
 AND kept_link.id < duplicate_link.id;

ALTER TABLE buddy_social_links
    ADD CONSTRAINT uq_buddy_social_link_type UNIQUE (profile_id, link_type);
