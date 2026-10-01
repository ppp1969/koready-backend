package koready_backend.place.domain;

import java.util.Locale;
import java.util.Optional;

public final class ServiceRegionAddressMapper {

	private ServiceRegionAddressMapper() {
	}

	public static Optional<ServiceRegionCode> fromAddress(String address) {
		if (address == null || address.isBlank()) {
			return Optional.empty();
		}
		String value = address.strip().replace(" ", "").toLowerCase(Locale.ROOT);
		if (value.startsWith("서울") || value.startsWith("seoul")) {
			return Optional.of(ServiceRegionCode.SEOUL);
		}
		if (value.startsWith("경기") || value.startsWith("인천")
			|| value.startsWith("gyeonggi") || value.startsWith("incheon")) {
			return Optional.of(ServiceRegionCode.GYEONGGI);
		}
		if (value.startsWith("강원") || value.startsWith("gangwon")) {
			return Optional.of(ServiceRegionCode.GANGWON);
		}
		if (value.startsWith("충북") || value.startsWith("충남")
			|| value.startsWith("충청") || value.startsWith("대전")
			|| value.startsWith("세종") || value.startsWith("chungcheong")
			|| value.startsWith("daejeon") || value.startsWith("sejong")) {
			return Optional.of(ServiceRegionCode.CHUNGCHEONG);
		}
		if (value.startsWith("전북") || value.startsWith("전남")
			|| value.startsWith("전라") || value.startsWith("광주")
			|| value.startsWith("jeolla") || value.startsWith("gwangju")) {
			return Optional.of(ServiceRegionCode.JEOLLA);
		}
		if (value.startsWith("경북") || value.startsWith("경남")
			|| value.startsWith("경상") || value.startsWith("부산")
			|| value.startsWith("대구") || value.startsWith("울산")
			|| value.startsWith("포항") || value.startsWith("gyeongsang")
			|| value.startsWith("busan") || value.startsWith("daegu")
			|| value.startsWith("ulsan") || value.startsWith("pohang")) {
			return Optional.of(ServiceRegionCode.GYEONGSANG);
		}
		if (value.startsWith("제주") || value.startsWith("jeju")) {
			return Optional.of(ServiceRegionCode.JEJU);
		}
		return Optional.empty();
	}
}
