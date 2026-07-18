INSERT INTO administrative_regions
    (provider, level, code, parent_code, name, service_region_code)
VALUES
    ('KTO_LDONG', 'SIDO', '12', '', '전남광주통합특별시', 'JEOLLA');

UPDATE places place
JOIN administrative_regions region
  ON region.provider = 'KTO_LDONG'
 AND region.level = 'SIDO'
 AND region.parent_code = ''
 AND region.code = CASE
        WHEN CHAR_LENGTH(place.ldong_regn_cd) > 2
            THEN LEFT(place.ldong_regn_cd, 2)
        ELSE place.ldong_regn_cd
     END
SET place.service_region_code = region.service_region_code
WHERE place.service_region_code IS NULL
  AND place.ldong_regn_cd IS NOT NULL;
