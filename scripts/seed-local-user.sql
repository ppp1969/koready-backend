INSERT INTO users (public_id, preferred_language, signup_status)
VALUES ('local-user', 'KO', 'NEED_ONBOARDING')
ON DUPLICATE KEY UPDATE deleted_at = NULL;

SET @local_user_id = (
    SELECT id
    FROM users
    WHERE public_id = 'local-user'
);

INSERT INTO user_locations
    (user_id, display_name, custom_label, provider, provider_place_id,
     road_address, address, latitude, longitude, sido, sigungu, dong,
     service_region_code)
SELECT
    @local_user_id,
    '성신여자대학교 (로컬 개발)',
    '학교',
    'KAKAO',
    'local-seongsin-university',
    '서울특별시 성북구 보문로34다길 2',
    '서울특별시 성북구 돈암동 173-1',
    37.5928,
    127.0165,
    '서울특별시',
    '성북구',
    '돈암동',
    'SEOUL'
WHERE NOT EXISTS (
    SELECT 1
    FROM user_locations
    WHERE user_id = @local_user_id
      AND display_name = '성신여자대학교 (로컬 개발)'
      AND deleted_at IS NULL
);

UPDATE user_locations
SET custom_label = '학교',
    provider = 'KAKAO',
    provider_place_id = 'local-seongsin-university',
    road_address = '서울특별시 성북구 보문로34다길 2',
    address = '서울특별시 성북구 돈암동 173-1',
    latitude = 37.5928,
    longitude = 127.0165,
    sido = '서울특별시',
    sigungu = '성북구',
    dong = '돈암동',
    service_region_code = 'SEOUL'
WHERE user_id = @local_user_id
  AND display_name = '성신여자대학교 (로컬 개발)'
  AND deleted_at IS NULL;

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
