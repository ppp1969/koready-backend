package koready_backend.place.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.PlaceSort;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

public interface PlaceQueryRepository {

	List<PlaceRow> findByRegion(PlaceListCriteria criteria);

	List<PlaceRow> search(PlaceSearchCriteria criteria);

	Optional<PlaceDetailRow> findDetail(long placeId, PlaceLanguage language);

	default List<PlaceImageRow> findImages(long placeId) {
		return List.of();
	}

	default PlaceDetailFacts findDetailFacts(long placeId) {
		return PlaceDetailFacts.empty();
	}

	default List<RelatedPlaceRow> findRelatedPlaces(
		long placeId,
		PlaceLanguage language,
		int limit
	) {
		return List.of();
	}

	default List<RelatedPlaceRow> findRelatedPlaceFallbacks(
		long placeId,
		PlaceLanguage language,
		List<Long> excludedPlaceIds,
		int limit
	) {
		return List.of();
	}

	default List<RelatedPlaceRow> findRelatedPlacesWithSameStyle(
		long placeId,
		PlaceLanguage language,
		List<Long> excludedPlaceIds,
		int limit
	) {
		return List.of();
	}

	record PlaceListCriteria(
		ServiceRegionCode serviceRegionCode,
		List<TravelStyle> travelStyles,
		PlaceSort sort,
		PlaceCursor cursor,
		int limit,
		PlaceLanguage language,
		LocalDate today
	) {
	}

	record PlaceSearchCriteria(
		String query,
		PlaceCursor cursor,
		int limit,
		PlaceLanguage language,
		LocalDate today
	) {
	}

	record PlaceCursor(
		int curationPriority,
		BigDecimal qualityScore,
		LocalDate deadlineSortDate,
		long placeId
	) {
	}

	record FestivalOccurrence(
		long occurrenceId,
		int eventYear,
		LocalDate startDate,
		LocalDate endDate
	) {
	}

	record PlaceRow(
		long placeId,
		String title,
		ServiceRegionCode serviceRegionCode,
		String serviceRegionName,
		String addressSummary,
		String imageUrl,
		TravelStyle travelStyle,
		String overview,
		int curationPriority,
		BigDecimal qualityScore,
		LocalDate deadlineSortDate,
		FestivalOccurrence festivalOccurrence
	) {
	}

	record PlaceDetailRow(
		long placeId,
		String title,
		ServiceRegionCode serviceRegionCode,
		String serviceRegionName,
		String address,
		BigDecimal latitude,
		BigDecimal longitude,
		String imageUrl,
		String overview,
		String translationSource
	) {
	}

	record PlaceImageRow(String imageUrl, String altText) {
	}

	record RelatedPlaceRow(
		long placeId,
		String title,
		String imageUrl,
		String shortDescription
	) {
	}

	record PlaceDetailFacts(
		String operatingHours,
		String operatingPeriod,
		String closedDays,
		String usageFee,
		String parkingInfo
	) {
		public static PlaceDetailFacts empty() {
			return new PlaceDetailFacts(null, null, null, null, null);
		}
	}
}
