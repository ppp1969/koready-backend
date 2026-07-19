INSERT INTO users (public_id, preferred_language, signup_status)
VALUES ('local-user', 'KO', 'NEED_ONBOARDING')
ON DUPLICATE KEY UPDATE deleted_at = NULL;

SET @local_user_id = (
    SELECT id
    FROM users
    WHERE public_id = 'local-user'
);

INSERT INTO user_locations (user_id, display_name, service_region_code)
SELECT @local_user_id, '성신여자대학교 (로컬 개발)', 'SEOUL'
WHERE NOT EXISTS (
    SELECT 1
    FROM user_locations
    WHERE user_id = @local_user_id
      AND display_name = '성신여자대학교 (로컬 개발)'
      AND deleted_at IS NULL
);

SET @local_location_id = (
    SELECT id
    FROM user_locations
    WHERE user_id = @local_user_id
      AND display_name = '성신여자대학교 (로컬 개발)'
      AND deleted_at IS NULL
    ORDER BY id
    LIMIT 1
);

UPDATE users
SET default_location_id = COALESCE(default_location_id, @local_location_id)
WHERE id = @local_user_id;

INSERT IGNORE INTO user_travel_styles
    (user_id, travel_style, display_order)
VALUES
    (@local_user_id, 'LOCAL_FOOD', 1),
    (@local_user_id, 'NATURE', 2);
