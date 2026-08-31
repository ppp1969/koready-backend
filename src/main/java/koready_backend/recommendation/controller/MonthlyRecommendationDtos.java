package koready_backend.recommendation.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import koready_backend.editorial.application.EditorialService;
import koready_backend.place.domain.PlaceLanguage;
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
		MonthlyRecommendationService.MonthlyRecommendationPage page,
		Map<Long, EditorialService.CardEditorialContent> editorialContents,
		PlaceLanguage language
	) {
		return new MonthlyRecommendationListResponse(
			page.year(),
			page.month(),
			from(page.appliedFilters()),
			page.items().stream()
				.map(card -> from(card, editorialContents.get(card.placeId()), language))
				.toList(),
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

	private static PlaceCardResponse from(
		MonthlyRecommendationService.PlaceCard card,
		EditorialService.CardEditorialContent editorial,
		PlaceLanguage language
	) {
		return new PlaceCardResponse(
			card.placeId(),
			card.title(),
			card.serviceRegionCode(),
			card.serviceRegionName(),
			card.addressSummary(),
			card.imageUrl(),
			from(card.festivalOccurrence()),
			card.operatingHours(),
			card.travelStyle(),
			editorial == null ? List.of() : editorial.tags().stream()
				.map(tag -> language == PlaceLanguage.EN ? tag.labelEn() : tag.labelKo())
				.toList(),
			editorial == null ? null : editorial.shortDescription(),
			card.saved());
	}

	private static FestivalOccurrenceResponse from(
		MonthlyRecommendationService.FestivalOccurrenceSummary occurrence
	) {
		if (occurrence == null) {
			return null;
		}
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
		String operatingHours,
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
