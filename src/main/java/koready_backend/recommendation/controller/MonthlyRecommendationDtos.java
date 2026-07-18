package koready_backend.recommendation.controller;

import java.time.LocalDate;
import java.util.List;

import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.MonthlyRecommendationService;
import koready_backend.recommendation.domain.DateFilterType;
import koready_backend.recommendation.domain.FestivalOccurrenceStatus;
import koready_backend.recommendation.domain.RecommendationSort;

final class MonthlyRecommendationDtos {

	private MonthlyRecommendationDtos() {
	}

	static MonthlyRecommendationListResponse from(
		MonthlyRecommendationService.MonthlyRecommendationPage page
	) {
		return new MonthlyRecommendationListResponse(
			page.year(),
			page.month(),
			from(page.appliedFilters()),
			page.items().stream().map(MonthlyRecommendationDtos::from).toList(),
			page.nextCursor(),
			page.hasMore(),
			page.totalCount());
	}

	private static MonthlyRecommendationFiltersResponse from(
		MonthlyRecommendationService.AppliedFilters filters
	) {
		return new MonthlyRecommendationFiltersResponse(
			filters.year(),
			filters.month(),
			filters.serviceRegionCode(),
			filters.dateFilterType(),
			filters.customStartDate(),
			filters.customEndDate(),
			filters.travelStyles(),
			filters.sort());
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
		return new FestivalOccurrenceResponse(
			occurrence.occurrenceId(),
			occurrence.eventYear(),
			occurrence.startDate(),
			occurrence.endDate(),
			occurrence.status(),
			occurrence.dateRangeText());
	}

	record MonthlyRecommendationListResponse(
		int year,
		int month,
		MonthlyRecommendationFiltersResponse appliedFilters,
		List<PlaceCardResponse> items,
		String nextCursor,
		boolean hasMore,
		long totalCount
	) {
	}

	record MonthlyRecommendationFiltersResponse(
		int year,
		int month,
		ServiceRegionCode serviceRegionCode,
		DateFilterType dateFilterType,
		LocalDate customStartDate,
		LocalDate customEndDate,
		List<TravelStyle> travelStyles,
		RecommendationSort sort
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
