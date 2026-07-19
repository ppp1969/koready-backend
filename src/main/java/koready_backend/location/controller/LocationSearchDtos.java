package koready_backend.location.controller;

import java.util.List;

import koready_backend.location.application.LocationSearchService;
import koready_backend.location.domain.LocationSearchResultType;
import koready_backend.place.domain.ServiceRegionCode;

final class LocationSearchDtos {

	private LocationSearchDtos() {
	}

	static LocationSearchResponse from(LocationSearchService.SearchResponse result) {
		return new LocationSearchResponse(result.items().stream()
			.map(LocationSearchDtos::from)
			.toList());
	}

	private static LocationSearchItem from(LocationSearchService.SearchItem item) {
		return new LocationSearchItem(
			item.searchResultToken(),
			"KAKAO",
			item.resultType(),
			item.providerPlaceId(),
			item.name(),
			item.roadAddress(),
			item.address(),
			item.latitude(),
			item.longitude(),
			item.sido(),
			item.sigungu(),
			item.dong(),
			item.serviceRegionCode());
	}

	record LocationSearchResponse(List<LocationSearchItem> items) {
	}

	record LocationSearchItem(
		String searchResultToken,
		String provider,
		LocationSearchResultType resultType,
		String providerPlaceId,
		String name,
		String roadAddress,
		String address,
		double latitude,
		double longitude,
		String sido,
		String sigungu,
		String dong,
		ServiceRegionCode serviceRegionCode
	) {
	}
}
