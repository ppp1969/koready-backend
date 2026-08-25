package koready_backend.editorial.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import koready_backend.editorial.domain.EditorialJobPriority;
import koready_backend.editorial.domain.EditorialCandidateStatusFilter;
import koready_backend.editorial.domain.EditorialCandidateRegionFilter;
import koready_backend.editorial.domain.EditorialCandidateSourceTrack;
import koready_backend.editorial.domain.EditorialJobStatus;
import koready_backend.editorial.domain.EditorialTriggerType;
import koready_backend.editorial.domain.EditorialLanguage;
import koready_backend.editorial.domain.TourismPurposeTag;

public interface EditorialRepository {

	EnqueueRecord enqueue(EnqueueCommand command);

	Optional<ReadyContentRecord> findReady(
		long placeId, EditorialLanguage language, String promptVersion);

	Optional<JobRecord> findLatestJob(long placeId, String promptVersion);

	List<CandidateRecord> findCandidates(CandidateQuery query);

	long countCandidates(CandidateQuery query);

	Optional<CandidateDetailRecord> findCandidate(long placeId);

	List<JobRecord> findJobs(JobQuery query);

	Optional<PlaceVisibilityRecord> updateVisibility(VisibilityCommand command);

	Optional<PlacePriorityRecord> updateCurationPriority(PriorityCommand command);

	Optional<PlaceImageOrderRecord> reorderImages(ImageOrderCommand command);

	record EnqueueCommand(
		long placeId,
		String promptVersion,
		EditorialTriggerType triggerType,
		EditorialJobPriority priority,
		String requestedBySubject,
		Instant requestedAt
	) {
	}

	record EnqueueRecord(
		String jobId,
		long placeId,
		EditorialJobStatus status,
		EditorialJobPriority priority,
		EditorialTriggerType triggerType,
		Instant requestedAt,
		boolean created
	) {
	}

	record ReadyContentRecord(
		long placeId,
		String sourceFingerprint,
		String promptVersion,
		String topic,
		String oneLineDescription,
		String shortIntroduction,
		List<String> enjoyPoints,
		List<TourismPurposeTag> tags,
		Instant generatedAt
	) {
	}

	record CandidateQuery(
		String query,
		EditorialCandidateStatusFilter status,
		EditorialCandidateRegionFilter region,
		Boolean hasKoreanOverview,
		Boolean queueEligible,
		EditorialCandidateSourceTrack sourceTrack,
		long startAfterPlaceId,
		int limit
	) {
	}

	record CandidateRecord(
		long placeId,
		String titleKo,
		String titleEn,
		String region,
		String imageUrl,
		boolean hasKoreanOverview,
		boolean queueEligible,
		EditorialCandidateSourceTrack sourceTrack,
		boolean hasTrustedEnglish,
		boolean active,
		boolean showFlag,
		int curationPriority,
		EditorialJobStatus status,
		Instant requestedAt
	) {
	}

	record CandidateDetailRecord(
		long placeId,
		String titleKo,
		String titleEn,
		String overviewKo,
		String address,
		String region,
		List<String> imageUrls,
		List<PlaceImageRecord> images,
		List<String> travelStyles,
		EditorialCandidateSourceTrack sourceTrack,
		boolean hasTrustedEnglish,
		boolean active,
		boolean showFlag,
		int curationPriority,
		EditorialJobStatus status,
		Instant requestedAt
	) {
	}

	record JobQuery(EditorialJobStatus status, long startAfterId, int limit) {
	}

	record JobRecord(
		long id,
		String jobId,
		long placeId,
		EditorialJobStatus status,
		EditorialJobPriority priority,
		EditorialTriggerType triggerType,
		int attemptCount,
		String errorCode,
		String errorMessage,
		Instant requestedAt,
		Instant startedAt,
		Instant completedAt
	) {
	}

	record VisibilityCommand(
		long placeId,
		boolean visible,
		String actorSubject,
		Instant updatedAt
	) {
	}

	record PlaceVisibilityRecord(
		long placeId,
		boolean active,
		boolean showFlag,
		Instant updatedAt
	) {
		public boolean visible() {
			return active && showFlag;
		}
	}

	record PriorityCommand(
		long placeId,
		int priority,
		String actorSubject,
		Instant updatedAt
	) {
	}

	record PlacePriorityRecord(long placeId, int priority, Instant updatedAt) {
	}

	record ImageOrderCommand(
		long placeId,
		List<Long> imageIds,
		String actorSubject,
		Instant updatedAt
	) {
	}

	record PlaceImageRecord(
		long imageId,
		String imageUrl,
		int displayOrder
	) {
	}

	record PlaceImageOrderRecord(
		long placeId,
		List<PlaceImageRecord> images,
		Instant updatedAt
	) {
	}
}
