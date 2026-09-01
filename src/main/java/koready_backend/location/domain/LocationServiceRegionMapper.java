package koready_backend.location.domain;

import java.util.Optional;

import koready_backend.place.domain.ServiceRegionCode;

public final class LocationServiceRegionMapper {

	private LocationServiceRegionMapper() {
	}

	public static Optional<ServiceRegionCode> fromSido(String sido) {
		if (sido == null || sido.isBlank()) {
			return Optional.empty();
		}
		String value = sido.strip().replace(" ", "").toLowerCase(java.util.Locale.ROOT);
		if (value.startsWith("서울")) {
			return Optional.of(ServiceRegionCode.SEOUL);
		}
		if (value.startsWith("경기") || value.startsWith("인천")) {
			return Optional.of(ServiceRegionCode.GYEONGGI);
		}
		if (value.startsWith("강원")) {
			return Optional.of(ServiceRegionCode.GANGWON);
		}
		if (value.startsWith("충북") || value.startsWith("충남")
			|| value.startsWith("충청") || value.startsWith("대전")
			|| value.startsWith("세종")) {
			return Optional.of(ServiceRegionCode.CHUNGCHEONG);
		}
		if (value.startsWith("전북") || value.startsWith("전남")
			|| value.startsWith("전라") || value.startsWith("광주")) {
			return Optional.of(ServiceRegionCode.JEOLLA);
		}
		if (value.startsWith("경북") || value.startsWith("경남")
			|| value.startsWith("경상") || value.startsWith("부산")
			|| value.startsWith("대구") || value.startsWith("울산")) {
			return Optional.of(ServiceRegionCode.GYEONGSANG);
		}
		if (value.startsWith("제주")) {
			return Optional.of(ServiceRegionCode.JEJU);
		}
		if (value.startsWith("seoul")) {
			return Optional.of(ServiceRegionCode.SEOUL);
		}
		if (value.startsWith("gyeonggi") || value.startsWith("incheon")) {
			return Optional.of(ServiceRegionCode.GYEONGGI);
		}
		if (value.startsWith("gangwon")) {
			return Optional.of(ServiceRegionCode.GANGWON);
		}
		if (value.startsWith("chungcheong") || value.startsWith("daejeon")
			|| value.startsWith("sejong")) {
			return Optional.of(ServiceRegionCode.CHUNGCHEONG);
		}
		if (value.startsWith("jeolla") || value.startsWith("gwangju")) {
			return Optional.of(ServiceRegionCode.JEOLLA);
		}
		if (value.startsWith("gyeongsang") || value.startsWith("busan")
			|| value.startsWith("daegu") || value.startsWith("ulsan")) {
			return Optional.of(ServiceRegionCode.GYEONGSANG);
		}
		if (value.startsWith("jeju")) {
			return Optional.of(ServiceRegionCode.JEJU);
		}
		return Optional.empty();
	}
}
