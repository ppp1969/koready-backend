START TRANSACTION;

CREATE TEMPORARY TABLE local_place_seed (
    source_id VARCHAR(100) NOT NULL,
    service_region_code VARCHAR(20) NOT NULL,
    address VARCHAR(500) NOT NULL,
    title_ko VARCHAR(300) NOT NULL,
    title_en VARCHAR(300) NOT NULL,
    overview_ko TEXT NOT NULL,
    overview_en TEXT NOT NULL,
    travel_style VARCHAR(40) NOT NULL,
    quality_score DECIMAL(5, 2) NOT NULL,
    PRIMARY KEY (source_id)
);

INSERT INTO local_place_seed
    (source_id, service_region_code, address, title_ko, title_en,
     overview_ko, overview_en, travel_style, quality_score)
VALUES
    (
        'LOCAL-DEMO-001', 'SEOUL', '서울특별시 종로구 사직로 161',
        '경복궁', 'Gyeongbokgung Palace',
        '조선 왕조의 중심 궁궐을 천천히 걸으며 한국의 역사와 건축을 살펴볼 수 있습니다.',
        'Walk through a central Joseon palace and explore Korean history and architecture.',
        'CULTURE_EXPERIENCE', 98.00
    ),
    (
        'LOCAL-DEMO-002', 'SEOUL', '서울특별시 종로구 창경궁로 88',
        '광장시장', 'Gwangjang Market',
        '시장 골목에서 다양한 한국 음식을 맛보고 활기찬 일상을 가까이에서 경험할 수 있습니다.',
        'Taste a range of Korean food and experience the energy of a traditional market.',
        'TRADITIONAL_MARKET', 96.00
    ),
    (
        'LOCAL-DEMO-003', 'SEOUL', '서울특별시 용산구 서빙고로 137',
        '국립중앙박물관', 'National Museum of Korea',
        '한국의 선사 시대부터 근현대까지 이어지는 문화유산을 한곳에서 만날 수 있습니다.',
        'Discover Korean cultural heritage from prehistory through the modern era.',
        'EXHIBITION_MUSEUM', 95.00
    ),
    (
        'LOCAL-DEMO-004', 'GYEONGGI', '경기도 수원시 팔달구 정조로 825',
        '수원 화성', 'Suwon Hwaseong Fortress',
        '성곽길을 따라 걸으며 도시 풍경과 조선 후기의 건축 기술을 함께 볼 수 있습니다.',
        'Walk the fortress walls for city views and late-Joseon architecture.',
        'CULTURE_EXPERIENCE', 94.00
    ),
    (
        'LOCAL-DEMO-005', 'GANGWON', '강원특별자치도 강릉시 창해로 514',
        '경포해변', 'Gyeongpo Beach',
        '넓은 모래사장과 동해 풍경을 즐기며 쉬어가기 좋은 강릉의 대표 해변입니다.',
        'Relax on a wide sandy beach with open views of Korea''s east coast.',
        'NATURE', 92.00
    ),
    (
        'LOCAL-DEMO-006', 'CHUNGCHEONG', '충청남도 공주시 웅진로 280',
        '공산성', 'Gongsanseong Fortress',
        '백제 시대의 역사와 금강 주변 풍경을 함께 만나는 성곽 산책 코스입니다.',
        'A fortress walk combining Baekje history with views along the Geum River.',
        'CULTURE_EXPERIENCE', 91.00
    ),
    (
        'LOCAL-DEMO-007', 'JEOLLA', '전북특별자치도 전주시 완산구 기린대로 99',
        '전주 한옥마을', 'Jeonju Hanok Village',
        '한옥 골목과 전통문화를 둘러보고 전주의 음식을 함께 즐길 수 있습니다.',
        'Explore hanok lanes, traditional culture, and the food of Jeonju.',
        'CULTURE_EXPERIENCE', 93.00
    ),
    (
        'LOCAL-DEMO-008', 'GYEONGSANG', '부산광역시 사하구 감내2로 203',
        '감천문화마을', 'Gamcheon Culture Village',
        '언덕 골목을 걸으며 다채로운 마을 풍경과 작은 전시 공간을 발견할 수 있습니다.',
        'Wander hillside alleys filled with colorful views and small art spaces.',
        'DRAMA_LOCATION', 90.00
    ),
    (
        'LOCAL-DEMO-009', 'JEJU', '제주특별자치도 서귀포시 성산읍 성산리 1',
        '성산일출봉', 'Seongsan Ilchulbong',
        '제주의 화산 지형과 바다를 한눈에 조망할 수 있는 대표 자연 명소입니다.',
        'See Jeju''s volcanic landscape and surrounding sea from a landmark peak.',
        'NATURE', 97.00
    ),
    (
        'LOCAL-DEMO-010', 'SEOUL', '서울특별시 중구 세종대로 110',
        '[로컬 테스트] KoReady 여름축제', '[Local Test] KoReady Summer Festival',
        '마감순 정렬과 축제 상태 화면을 확인하기 위한 로컬 개발 전용 데이터입니다.',
        'Local-only data for testing festival status and deadline sorting.',
        'LOCAL_FESTIVAL', 70.00
    );

INSERT INTO places
    (kto_content_id, service_region_code, address, show_flag, active, data_quality_score)
SELECT
    source_id, service_region_code, address, TRUE, TRUE, quality_score
FROM local_place_seed
ON DUPLICATE KEY UPDATE
    service_region_code = VALUES(service_region_code),
    address = VALUES(address),
    show_flag = TRUE,
    active = TRUE,
    data_quality_score = VALUES(data_quality_score);

INSERT INTO place_localizations
    (place_id, language, title, overview, address_text, translation_source)
SELECT
    place.id, 'KO', seed.title_ko, seed.overview_ko, seed.address, 'MANUAL_EDITED'
FROM local_place_seed seed
JOIN places place ON place.kto_content_id = seed.source_id
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    overview = VALUES(overview),
    address_text = VALUES(address_text),
    translation_source = VALUES(translation_source);

INSERT INTO place_localizations
    (place_id, language, title, overview, address_text, translation_source)
SELECT
    place.id, 'EN', seed.title_en, seed.overview_en, seed.address, 'MANUAL_EDITED'
FROM local_place_seed seed
JOIN places place ON place.kto_content_id = seed.source_id
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    overview = VALUES(overview),
    address_text = VALUES(address_text),
    translation_source = VALUES(translation_source);

INSERT INTO place_style_mappings
    (place_id, travel_style, source, confidence)
SELECT
    place.id, seed.travel_style, 'MANUAL', 1.0000
FROM local_place_seed seed
JOIN places place ON place.kto_content_id = seed.source_id
ON DUPLICATE KEY UPDATE
    source = VALUES(source),
    confidence = VALUES(confidence);

INSERT INTO place_style_mappings
    (place_id, travel_style, source, confidence)
SELECT place.id, 'LOCAL_FOOD', 'MANUAL', 1.0000
FROM places place
WHERE place.kto_content_id = 'LOCAL-DEMO-002'
ON DUPLICATE KEY UPDATE
    source = VALUES(source),
    confidence = VALUES(confidence);

INSERT INTO place_event_occurrences
    (place_id, event_year, occurrence_sequence, start_date, end_date,
     event_place, provider, source_content_id, source_operation,
     visible_from, date_validation_status)
SELECT
    place.id,
    YEAR(DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY)),
    1,
    DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY),
    DATE_ADD(CURRENT_DATE, INTERVAL 10 DAY),
    place.address,
    'MANUAL',
    'LOCAL-DEMO-FESTIVAL',
    'LOCAL_SEED',
    DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY),
    'VALID'
FROM places place
WHERE place.kto_content_id = 'LOCAL-DEMO-010'
ON DUPLICATE KEY UPDATE
    start_date = VALUES(start_date),
    end_date = VALUES(end_date),
    event_place = VALUES(event_place),
    visible_from = VALUES(visible_from),
    date_validation_status = VALUES(date_validation_status);

DROP TEMPORARY TABLE local_place_seed;

COMMIT;
