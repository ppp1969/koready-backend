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
}
