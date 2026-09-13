UPDATE places place
LEFT JOIN administrative_regions area_region
  ON area_region.provider = 'KTO'
 AND area_region.level = 'SIDO'
 AND area_region.parent_code = ''
 AND area_region.code = place.area_code
LEFT JOIN administrative_regions legal_region
  ON legal_region.provider = 'KTO_LDONG'
 AND legal_region.level = 'SIDO'
 AND legal_region.parent_code = ''
 AND legal_region.code = LEFT(place.ldong_regn_cd, 2)
SET place.service_region_code = COALESCE(
    area_region.service_region_code,
    legal_region.service_region_code
)
WHERE place.service_region_code IS NULL
  AND COALESCE(
      area_region.service_region_code,
      legal_region.service_region_code
  ) IS NOT NULL;
