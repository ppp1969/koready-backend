package koready_backend.onboarding.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.user.domain.SignupStatus;

public interface OnboardingProfileRepository {

	Optional<UserRecord> findUserByPublicId(String publicId);

	Optional<UserRecord> findUserByPublicIdForUpdate(String publicId);

	Optional<LocationRecord> findOwnedLocation(long userId, long locationId);

	List<TravelStyle> findTravelStyles(long userId);

	Optional<CandidateSetRecord> findCandidateSet(String publicId);

	Set<Long> findCandidatePlaceIds(long candidateSetId);

	Optional<SelectionRecord> findSelection(long userId);

	void replaceTravelStyles(long userId, List<TravelStyle> styles, Instant now);

	void replaceSelections(
		long userId,
		long candidateSetId,
		List<Long> placeIds,
		Instant now
	);

	void completeUser(long userId, long defaultLocationId, Instant completedAt);

	record UserRecord(
		long userId,
		SignupStatus signupStatus,
		Long defaultLocationId,
		Instant completedAt
	) {
	}

	record LocationRecord(
		long locationId,
		String displayName,
		ServiceRegionCode serviceRegionCode,
		boolean active
	) {
	}

	record CandidateSetRecord(
		long candidateSetId,
		String publicId,
		Integer version,
		CandidateSetStatus status,
		Instant publishedAt
	) {
	}

	record SelectionRecord(
		long candidateSetId,
		String candidateSetPublicId,
		int candidateSetVersion,
		List<Long> placeIds
	) {
		public SelectionRecord {
			placeIds = List.copyOf(placeIds);
		}
	}
}
