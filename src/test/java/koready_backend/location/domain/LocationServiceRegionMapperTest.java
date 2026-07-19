package koready_backend.location.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import koready_backend.place.domain.ServiceRegionCode;

class LocationServiceRegionMapperTest {

	@Test
	void mapsAllKoreanSidoNamesToSevenServiceRegions() {
		Map<String, ServiceRegionCode> expectations = Map.ofEntries(
			Map.entry("서울특별시", ServiceRegionCode.SEOUL),
			Map.entry("인천광역시", ServiceRegionCode.GYEONGGI),
			Map.entry("경기도", ServiceRegionCode.GYEONGGI),
			Map.entry("강원특별자치도", ServiceRegionCode.GANGWON),
			Map.entry("대전광역시", ServiceRegionCode.CHUNGCHEONG),
			Map.entry("세종특별자치시", ServiceRegionCode.CHUNGCHEONG),
			Map.entry("충청북도", ServiceRegionCode.CHUNGCHEONG),
			Map.entry("충남", ServiceRegionCode.CHUNGCHEONG),
			Map.entry("광주광역시", ServiceRegionCode.JEOLLA),
			Map.entry("전북특별자치도", ServiceRegionCode.JEOLLA),
			Map.entry("전라남도", ServiceRegionCode.JEOLLA),
			Map.entry("부산광역시", ServiceRegionCode.GYEONGSANG),
			Map.entry("대구광역시", ServiceRegionCode.GYEONGSANG),
			Map.entry("울산광역시", ServiceRegionCode.GYEONGSANG),
			Map.entry("경상북도", ServiceRegionCode.GYEONGSANG),
			Map.entry("경남", ServiceRegionCode.GYEONGSANG),
			Map.entry("제주특별자치도", ServiceRegionCode.JEJU));

		expectations.forEach((sido, expected) -> assertEquals(
			expected,
			LocationServiceRegionMapper.fromSido(sido).orElseThrow(),
			sido));
	}

	@Test
	void rejectsBlankAndUnsupportedRegions() {
		assertTrue(LocationServiceRegionMapper.fromSido(null).isEmpty());
		assertTrue(LocationServiceRegionMapper.fromSido("  ").isEmpty());
		assertTrue(LocationServiceRegionMapper.fromSido("Tokyo").isEmpty());
	}
}
