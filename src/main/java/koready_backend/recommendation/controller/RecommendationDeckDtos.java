package koready_backend.recommendation.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.RecommendationDeckService;
import koready_backend.recommendation.domain.RecommendationScope;

final class RecommendationDeckDtos {

	private RecommendationDeckDtos() {
	}

	static RecommendationDeckResponse from(
		RecommendationDeckService.RecommendationDeckPage page
	) {
		return new RecommendationDeckResponse(
			page.deckId(),
			page.scope(),
			new LocationSummaryResponse(
				page.originLocation().locationId(),
				page.originLocation().displayName(),
				page.originLocation().serviceRegionCode()),
			page.cards().stream().map(RecommendationDeckDtos::from).toList(),
			page.nextCursor(),
			page.hasMore(),
			page.remainingThreshold(),
			new DeduplicationResponse(
				page.deduplication().guaranteedWithinDeck(),
				page.deduplication().suppressionDays()));
	}

	private static RecommendationCardResponse from(
		RecommendationDeckService.RecommendationCard card
	) {
		return new RecommendationCardResponse(
			card.placeId(),
			card.title(),
			card.locationText(),
			card.imageUrl(),
			card.saved(),
			card.tags(),
			card.shortDescription(),
			card.serviceRegionCode(),
			card.travelStyle(),
			card.matchRank(),
			new MatchReasonResponse(
				card.matchReason().travelStyleMatched(),
				card.matchReason().preferenceTagMatched(),
				card.matchReason().matchedTagCodes()));
	}

	record CreateRecommendationDeckRequest(
		@NotNull RecommendationScope scope,
		Long originLocationId,
		@NotNull @Min(1) @Max(50) Integer size
	) {
	}

	record RecommendationDeckResponse(
		String deckId,
		RecommendationScope scope,
		LocationSummaryResponse originLocation,
		List<RecommendationCardResponse> cards,
		String nextCursor,
		boolean hasMore,
		int remainingThreshold,
		DeduplicationResponse deduplication
	) {
	}

	record LocationSummaryResponse(
		long locationId,
		String displayName,
		ServiceRegionCode serviceRegionCode
	) {
	}

	record RecommendationCardResponse(
		long placeId,
		String title,
		String locationText,
		String imageUrl,
		boolean saved,
		List<String> tags,
		String shortDescription,
		ServiceRegionCode serviceRegionCode,
		TravelStyle travelStyle,
		int matchRank,
		MatchReasonResponse matchReason
	) {
	}

	record MatchReasonResponse(
		boolean travelStyleMatched,
		boolean preferenceTagMatched,
		List<String> matchedTagCodes
	) {
	}

	record DeduplicationResponse(
		boolean guaranteedWithinDeck,
		int suppressionDays
	) {
	}
}
