package koready_backend.buddy.controller;

import java.util.List;

import koready_backend.buddy.application.BuddyMateService;

final class PlaceMateDtos {

	private PlaceMateDtos() {
	}

	static PlaceMateListResponse from(BuddyMateService.PlaceMatePage page) {
		return new PlaceMateListResponse(
			page.placeId(),
			page.items().stream().map(BuddyProfileDtos::from).toList(),
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
