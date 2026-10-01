package koready_backend.place.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ServiceRegionAddressMapperTest {

	@Test
	void mapsKoreanFestivalAddressesToSevenServiceRegions() {
		Map<String, ServiceRegionCode> cases = Map.ofEntries(
			Map.entry("서울특별시 강동구", ServiceRegionCode.SEOUL),
			Map.entry("경기도 수원시", ServiceRegionCode.GYEONGGI),
			Map.entry("인천광역시 부평구", ServiceRegionCode.GYEONGGI),
			Map.entry("강원특별자치도 강릉시", ServiceRegionCode.GANGWON),
			Map.entry("충청남도 계룡시", ServiceRegionCode.CHUNGCHEONG),
			Map.entry("대전광역시 유성구", ServiceRegionCode.CHUNGCHEONG),
			Map.entry("세종특별자치시 다솜로", ServiceRegionCode.CHUNGCHEONG),
			Map.entry("전북특별자치도 김제시", ServiceRegionCode.JEOLLA),
			Map.entry("전남광주통합특별시 서구", ServiceRegionCode.JEOLLA),
			Map.entry("경상북도 김천시", ServiceRegionCode.GYEONGSANG),
			Map.entry("부산광역시 동래구", ServiceRegionCode.GYEONGSANG),
			Map.entry("포항시 북구 두호동", ServiceRegionCode.GYEONGSANG),
			Map.entry("제주특별자치도 제주시", ServiceRegionCode.JEJU));

		cases.forEach((address, region) -> assertEquals(
			region, ServiceRegionAddressMapper.fromAddress(address).orElseThrow(), address));
	}

	@Test
	void leavesMissingOrAmbiguousAddressesUnmapped() {
		assertTrue(ServiceRegionAddressMapper.fromAddress(null).isEmpty());
		assertTrue(ServiceRegionAddressMapper.fromAddress("온라인 행사").isEmpty());
	}
}
