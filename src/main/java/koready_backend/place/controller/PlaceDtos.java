package koready_backend.place.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import koready_backend.editorial.application.EditorialService;
import koready_backend.editorial.domain.EditorialJobStatus;
import koready_backend.editorial.domain.TourismPurposeTag;
import koready_backend.place.application.PlaceQueryService;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

final class PlaceDtos {

	private PlaceDtos() {
	}

	static PlaceListResponse from(
		PlaceQueryService.PlacePage page,
		Map<Long, EditorialService.CardEditorialContent> editorialContents,
		PlaceLanguage language
	) {
		return new PlaceListResponse(
			page.items().stream()
				.map(card -> from(card, editorialContents.get(card.placeId()), language))
				.toList(),
			page.nextCursor(),
			page.hasMore(),
			page.totalCount());
	}

	static PlaceDetailResponse from(
		PlaceQueryService.PlaceDetail detail,
		EditorialService.PublicEditorial editorial,
		Map<Long, EditorialService.CardEditorialContent> relatedEditorialContents,
		PlaceLanguage language
	) {
		EditorialService.EditorialContent content = editorial.content();
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
			content == null ? List.of() : content.tags().stream()
				.map(tag -> tag(tag, language)).toList(),
			detail.isSaved(),
			from(content),
			editorial.status(),
			detail.relatedPlaces().stream()
				.map(place -> from(place, relatedEditorialContents.get(place.placeId())))
				.toList(),
			availableTabs(detail, content));
	}

	private static List<String> availableTabs(
		PlaceQueryService.PlaceDetail detail,
		EditorialService.EditorialContent content
	) {
		var tabs = new java.util.ArrayList<String>();
		if (content != null) {
			tabs.add("DESCRIPTION");
		}
		if (detail.latitude() != null && detail.longitude() != null) {
			tabs.add("ROUTE");
		}
		tabs.add("MATES");
		return List.copyOf(tabs);
	}

	private static PlaceCardResponse from(
		PlaceQueryService.PlaceCard card,
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
			card.travelStyle(),
			editorial == null ? card.tags() : editorial.tags().stream()
				.map(tag -> language == PlaceLanguage.EN ? tag.labelEn() : tag.labelKo())
				.toList(),
			editorial == null ? card.shortDescription() : editorial.shortDescription(),
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

	private static PlaceDescriptionResponse from(EditorialService.EditorialContent content) {
		if (content == null) {
			return null;
		}
		return new PlaceDescriptionResponse(
			content.topic(),
			content.oneLineDescription(),
			content.shortIntroduction(),
			content.enjoyPoints(),
			content.contentVersion());
	}

	private static PlaceTagResponse tag(TourismPurposeTag tag, PlaceLanguage language) {
		return new PlaceTagResponse(
			tag.name(), language == PlaceLanguage.EN ? tag.labelEn() : tag.labelKo());
	}

	private static RelatedPlaceResponse from(
		PlaceQueryService.RelatedPlace place,
		EditorialService.CardEditorialContent editorial
	) {
		return new RelatedPlaceResponse(
			place.placeId(), place.title(), place.imageUrl(),
			editorial == null ? place.shortDescription() : editorial.shortDescription());
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
		List<PlaceTagResponse> tags,
		boolean isSaved,
		PlaceDescriptionResponse description,
		EditorialJobStatus editorialStatus,
		List<RelatedPlaceResponse> relatedPlaces,
		List<String> availableTabs
	) {
	}

	record PlaceImageResponse(String imageUrl, int order, String altText) {
	}

	record PlaceDescriptionResponse(
		String topic,
		String oneLineDescription,
		String shortIntroduction,
		List<String> enjoyPoints,
		String contentVersion
	) {
	}

	record PlaceTagResponse(String code, String label) {
	}

	record RelatedPlaceResponse(
		long placeId,
		String title,
		String imageUrl,
		String shortDescription
	) {
	}
}
