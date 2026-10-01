UPDATE places place
SET place.service_region_code = CASE
    WHEN place.kto_content_id = '2755427' THEN 'SEOUL'
    WHEN COALESCE(NULLIF(TRIM(place.road_address), ''), NULLIF(TRIM(place.address), ''))
         REGEXP '^(서울|Seoul)' THEN 'SEOUL'
    WHEN COALESCE(NULLIF(TRIM(place.road_address), ''), NULLIF(TRIM(place.address), ''))
         REGEXP '^(경기|인천|Gyeonggi|Incheon)' THEN 'GYEONGGI'
    WHEN COALESCE(NULLIF(TRIM(place.road_address), ''), NULLIF(TRIM(place.address), ''))
         REGEXP '^(강원|Gangwon)' THEN 'GANGWON'
    WHEN COALESCE(NULLIF(TRIM(place.road_address), ''), NULLIF(TRIM(place.address), ''))
         REGEXP '^(충북|충남|충청|대전|세종|Chungcheong|Daejeon|Sejong)' THEN 'CHUNGCHEONG'
    WHEN COALESCE(NULLIF(TRIM(place.road_address), ''), NULLIF(TRIM(place.address), ''))
         REGEXP '^(전북|전남|전라|광주|Jeolla|Gwangju)' THEN 'JEOLLA'
    WHEN COALESCE(NULLIF(TRIM(place.road_address), ''), NULLIF(TRIM(place.address), ''))
         REGEXP '^(경북|경남|경상|부산|대구|울산|포항|Gyeongsang|Busan|Daegu|Ulsan|Pohang)'
         THEN 'GYEONGSANG'
    WHEN COALESCE(NULLIF(TRIM(place.road_address), ''), NULLIF(TRIM(place.address), ''))
         REGEXP '^(제주|Jeju)' THEN 'JEJU'
    ELSE place.service_region_code
END
WHERE place.service_region_code IS NULL;
