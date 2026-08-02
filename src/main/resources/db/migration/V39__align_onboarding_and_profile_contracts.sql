ALTER TABLE user_locations
    ADD COLUMN postal_code VARCHAR(20) NULL AFTER address;

ALTER TABLE user_travel_styles
    DROP CHECK chk_user_travel_style_order,
    ADD CONSTRAINT chk_user_travel_style_order
        CHECK (display_order BETWEEN 1 AND 4);

ALTER TABLE buddy_social_links
    DROP CHECK chk_buddy_social_link_type,
    ADD CONSTRAINT chk_buddy_social_link_type CHECK (
        link_type IN (
            'INSTAGRAM',
            'TIKTOK',
            'WECHAT',
            'XIAOHONGSHU',
            'LINE',
            'KAKAOTALK'
        )
    );

ALTER TABLE onboarding_candidate_set_items
    DROP CHECK chk_onboarding_candidate_item_tags,
    ADD CONSTRAINT chk_onboarding_candidate_item_tags
        CHECK (
            JSON_TYPE(display_tags_json) = 'ARRAY'
            AND JSON_LENGTH(display_tags_json) BETWEEN 2 AND 3
        );
