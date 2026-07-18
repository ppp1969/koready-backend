package koready_backend.place.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import koready_backend.place.application.port.PlaceQueryRepository.FestivalOccurrence;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.SavedPlaceSource;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

public interface SavedPlaceRepository {

	Optional<Long> findActiveUserId(String publicId);

	boolean existsVisiblePlace(long placeId);

	SavedPlaceRecord save(
		long userId,
		long placeId,
		SavedPlaceSource source,
		Instant savedAt
	);

	void unsave(long userId, long placeId, Instant deletedAt);

	List<SavedPlaceRow> findAll(SavedPlaceCriteria criteria);

	record SavedPlaceCriteria(
		long userId,
		SavedPlaceCursor cursor,
		int limit,
		PlaceLanguage language,
		LocalDate today
	) {
	}

	record SavedPlaceCursor(Instant savedAt, long savedPlaceId) {
	}

	record SavedPlaceRecord(long placeId, Instant savedAt) {
	}

	record SavedPlaceRow(
		long savedPlaceId,
		long placeId,
		Instant savedAt,
		String title,
		ServiceRegionCode serviceRegionCode,
		String serviceRegionName,
		String addressSummary,
		String imageUrl,
		TravelStyle travelStyle,
		String overview,
		BigDecimal qualityScore,
		FestivalOccurrence festivalOccurrence
	) {
	}
}
