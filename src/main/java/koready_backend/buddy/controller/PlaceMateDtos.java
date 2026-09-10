package koready_backend.buddy.controller;

import java.util.List;

import koready_backend.buddy.application.BuddyMateService;
import koready_backend.place.domain.PlaceLanguage;

final class PlaceMateDtos {

	private PlaceMateDtos() {
	}

	static PlaceMateListResponse from(
		BuddyMateService.PlaceMatePage page,
		PlaceLanguage language
	) {
		return new PlaceMateListResponse(
			page.placeId(),
			page.items().stream().map(item -> BuddyProfileDtos.from(item, language)).toList(),
			page.nextCursor(),
			page.hasMore());
	}

	record PlaceMateListResponse(
		long placeId,
		List<BuddyProfileDtos.BuddyProfileResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}
}
