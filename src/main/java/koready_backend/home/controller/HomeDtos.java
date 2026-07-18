package koready_backend.home.controller;

import java.time.LocalDate;
import java.util.List;

import koready_backend.home.application.HomeService;
import koready_backend.home.application.port.HomeRepository.HomeLocation;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.MonthlyRecommendationService;
import koready_backend.recommendation.domain.FestivalOccurrenceStatus;

final class HomeDtos {

	private HomeDtos() {
	}

	static HomeResponse from(HomeService.Home home) {
		return new HomeResponse(
			from(home.currentLocation()),
			home.preferredLanguage(),
			new MonthlyRecommendationPreviewResponse(
				home.monthlyRecommendation().year(),
				home.monthlyRecommendation().month(),
				home.monthlyRecommendation().title(),
				home.monthlyRecommendation().totalCount(),
				home.monthlyRecommendation().items().stream()
					.map(HomeDtos::from)
					.toList()));
	}

	private static LocationSummaryResponse from(HomeLocation location) {
		return location == null
			? null
			: new LocationSummaryResponse(
				location.locationId(),
				location.displayName(),
				location.serviceRegionCode());
	}

	private static PlaceCardResponse from(MonthlyRecommendationService.PlaceCard card) {
		return new PlaceCardResponse(
			card.placeId(),
			card.title(),
			card.serviceRegionCode(),
			card.serviceRegionName(),
			card.addressSummary(),
			card.imageUrl(),
			from(card.festivalOccurrence()),
			card.travelStyle(),
			card.tags(),
			card.shortDescription(),
			card.saved());
	}

	private static FestivalOccurrenceResponse from(
		MonthlyRecommendationService.FestivalOccurrenceSummary occurrence
	) {
		return occurrence == null
			? null
			: new FestivalOccurrenceResponse(
				occurrence.occurrenceId(),
				occurrence.eventYear(),
				occurrence.startDate(),
				occurrence.endDate(),
				occurrence.status(),
				occurrence.dateRangeText());
	}

	record HomeResponse(
		LocationSummaryResponse currentLocation,
		PlaceLanguage preferredLanguage,
		MonthlyRecommendationPreviewResponse monthlyRecommendation
	) {
	}

	record LocationSummaryResponse(
		long locationId,
		String displayName,
		ServiceRegionCode serviceRegionCode
	) {
	}

	record MonthlyRecommendationPreviewResponse(
		int year,
		int month,
		String title,
		long totalCount,
		List<PlaceCardResponse> items
	) {
	}

	record PlaceCardResponse(
		long placeId,
		String title,
		ServiceRegionCode serviceRegionCode,
		String serviceRegionName,
		String addressSummary,
		String imageUrl,
		FestivalOccurrenceResponse festivalOccurrence,
		TravelStyle travelStyle,
		List<String> tags,
		String shortDescription,
		boolean saved
	) {
	}

	record FestivalOccurrenceResponse(
		long occurrenceId,
		int eventYear,
		LocalDate startDate,
		LocalDate endDate,
		FestivalOccurrenceStatus status,
		String dateRangeText
	) {
	}
}
