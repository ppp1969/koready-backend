INSERT INTO users (public_id, preferred_language, signup_status, deleted_at)
VALUES ('local-buddy-demo', 'EN', 'COMPLETED', NULL)
ON DUPLICATE KEY UPDATE
    preferred_language = 'EN',
    signup_status = 'COMPLETED',
    deleted_at = NULL,
    updated_at = NOW(6);

SET @local_buddy_user_id = (
    SELECT id
    FROM users
    WHERE public_id = 'local-buddy-demo'
);

INSERT INTO buddy_profiles (
    user_id,
    profile_image_url,
    nickname,
    nationality,
    korean_level,
    bio,
    profile_public,
    sns_public,
    allows_messages,
    created_at,
    updated_at
)
VALUES (
    @local_buddy_user_id,
    NULL,
    'KoReady Demo Buddy',
    NULL,
    'INTERMEDIATE',
    'Demo profile for local frontend testing.',
    TRUE,
    TRUE,
    TRUE,
    NOW(6),
    NOW(6)
)
ON DUPLICATE KEY UPDATE
    profile_image_url = NULL,
    nickname = 'KoReady Demo Buddy',
    nationality = NULL,
    korean_level = 'INTERMEDIATE',
    bio = 'Demo profile for local frontend testing.',
    profile_public = TRUE,
    sns_public = TRUE,
    allows_messages = TRUE,
    updated_at = NOW(6);

SET @local_buddy_profile_id = (
    SELECT id
    FROM buddy_profiles
    WHERE user_id = @local_buddy_user_id
);

DELETE FROM buddy_blocks
WHERE blocker_user_id = @local_buddy_user_id
   OR blocked_user_id = @local_buddy_user_id;

DELETE FROM buddy_profile_languages
WHERE profile_id = @local_buddy_profile_id;

INSERT INTO buddy_profile_languages (profile_id, language_code, display_order)
VALUES
    (@local_buddy_profile_id, 'EN', 1),
    (@local_buddy_profile_id, 'KO', 2);

DELETE FROM buddy_profile_styles
WHERE profile_id = @local_buddy_profile_id;

INSERT INTO buddy_profile_styles (profile_id, buddy_style, display_order)
VALUES
    (@local_buddy_profile_id, 'FOODIE', 1),
    (@local_buddy_profile_id, 'PHOTOGRAPHY', 2);

DELETE FROM buddy_social_links
WHERE profile_id = @local_buddy_profile_id;

INSERT INTO buddy_social_links (profile_id, link_type, link_value, display_order)
VALUES (@local_buddy_profile_id, 'INSTAGRAM', '@koready_demo', 1);

SELECT @local_buddy_profile_id AS local_buddy_profile_id;
