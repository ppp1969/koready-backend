package koready_backend.place.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import koready_backend.place.application.SavedPlaceService;
import koready_backend.place.domain.SavedPlaceSource;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

final class SavedPlaceDtos {

	private SavedPlaceDtos() {
	}

	static SavePlaceResponse from(SavedPlaceService.SaveResult result) {
		return new SavePlaceResponse(result.placeId(), result.saved(), result.savedAt());
	}

	static SavedPlaceListResponse from(SavedPlaceService.SavedPlacePage page) {
		return new SavedPlaceListResponse(
			page.items().stream().map(SavedPlaceDtos::from).toList(),
			page.nextCursor(),
			page.hasMore());
	}

	private static SavedPlaceCardResponse from(SavedPlaceService.SavedPlaceCard card) {
		return new SavedPlaceCardResponse(
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
			card.saved(),
			card.savedAt());
	}

	private static FestivalOccurrenceResponse from(
		SavedPlaceService.FestivalOccurrenceSummary occurrence
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

	record SavePlaceRequest(@NotNull SavedPlaceSource source) {
	}

	record SavePlaceResponse(long placeId, boolean saved, Instant savedAt) {
	}

	record SavedPlaceListResponse(
		List<SavedPlaceCardResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record SavedPlaceCardResponse(
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
		boolean saved,
		Instant savedAt
	) {
	}

	record FestivalOccurrenceResponse(
		long occurrenceId,
		int eventYear,
		LocalDate startDate,
		LocalDate endDate,
		String status,
		String dateRangeText
	) {
	}
}
