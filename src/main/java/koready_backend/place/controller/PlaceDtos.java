package koready_backend.place.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import koready_backend.place.application.PlaceQueryService;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

final class PlaceDtos {

	private PlaceDtos() {
	}

	static PlaceListResponse from(PlaceQueryService.PlacePage page) {
		return new PlaceListResponse(
			page.items().stream().map(PlaceDtos::from).toList(),
			page.nextCursor(),
			page.hasMore(),
			page.totalCount());
	}

	static PlaceDetailResponse from(PlaceQueryService.PlaceDetail detail) {
		return new PlaceDetailResponse(
			detail.placeId(),
			detail.title(),
			detail.serviceRegionCode(),
			detail.locationText(),
			detail.address(),
			detail.latitude(),
			detail.longitude(),
			detail.operatingHours(),
			detail.operatingPeriod(),
			detail.closedDays(),
			detail.usageFee(),
			detail.parkingInfo(),
			detail.images().stream().map(PlaceDtos::from).toList(),
			detail.tags(),
			detail.isSaved(),
			from(detail.description()),
			detail.relatedPlaces().stream().map(PlaceDtos::from).toList(),
			detail.availableTabs());
	}

	private static PlaceCardResponse from(PlaceQueryService.PlaceCard card) {
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
		PlaceQueryService.FestivalOccurrenceSummary occurrence
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

	private static PlaceImageResponse from(PlaceQueryService.PlaceImage image) {
		return new PlaceImageResponse(image.imageUrl(), image.order(), image.altText());
	}

	private static PlaceDescriptionResponse from(PlaceQueryService.PlaceDescription description) {
		if (description == null) {
			return null;
		}
		return new PlaceDescriptionResponse(
			description.impactTitle(),
			description.impactSubtitle(),
			description.introParagraphs(),
			description.enjoyPoints(),
			description.sourceType());
	}

	private static RelatedPlaceResponse from(PlaceQueryService.RelatedPlace place) {
		return new RelatedPlaceResponse(
			place.placeId(), place.title(), place.imageUrl(), place.shortDescription());
	}

	record PlaceListResponse(
		List<PlaceCardResponse> items,
		String nextCursor,
		boolean hasMore,
		Integer totalCount
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
		String status,
		String dateRangeText
	) {
	}

	record PlaceDetailResponse(
		long placeId,
		String title,
		ServiceRegionCode serviceRegionCode,
		String locationText,
		String address,
		BigDecimal latitude,
		BigDecimal longitude,
		String operatingHours,
		String operatingPeriod,
		String closedDays,
		String usageFee,
		String parkingInfo,
		List<PlaceImageResponse> images,
		List<String> tags,
		boolean isSaved,
		PlaceDescriptionResponse description,
		List<RelatedPlaceResponse> relatedPlaces,
		List<String> availableTabs
	) {
	}

	record PlaceImageResponse(String imageUrl, int order, String altText) {
	}

	record PlaceDescriptionResponse(
		String impactTitle,
		String impactSubtitle,
		List<String> introParagraphs,
		List<String> enjoyPoints,
		String sourceType
	) {
	}

	record RelatedPlaceResponse(
		long placeId,
		String title,
		String imageUrl,
		String shortDescription
	) {
	}
}
