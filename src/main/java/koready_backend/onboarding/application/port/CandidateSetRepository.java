package koready_backend.onboarding.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import koready_backend.onboarding.domain.CandidatePlaceReadiness;
import koready_backend.onboarding.domain.CandidateSetDraft;
import koready_backend.onboarding.domain.CandidateSetItemDraft;
import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

public interface CandidateSetRepository {

	CandidateSetRecord insertDraft(String publicId, String title, Instant now);

	Optional<CandidateSetRecord> findByPublicId(String publicId);

	Optional<CandidateSetRecord> findByPublicIdForUpdate(String publicId);

	Optional<CandidateSetRecord> findCurrent();

	List<CandidateSetRecord> findPage(CandidateSetStatus status, Long beforeId, int limit);

	List<CandidateItemRecord> findItems(long candidateSetId);

	void replaceDraft(long candidateSetId, CandidateSetDraft draft, Instant now);

	Map<Long, CandidatePlaceReadiness> findPlaceReadiness(
		List<CandidateSetItemDraft> items);

	boolean markPublished(long candidateSetId, String actorSubject, Instant now);

	void replaceCurrent(long candidateSetId, Instant now);

	boolean markArchived(long candidateSetId, String actorSubject, Instant now);

	void clearCurrent(long candidateSetId);

	void recordAudit(AuditRecord audit);

	record CandidateSetRecord(
		long id,
		String publicId,
		String title,
		int version,
		CandidateSetStatus status,
		Instant publishedAt,
		String publishedBySubject,
		Instant archivedAt,
		Instant createdAt,
		Instant updatedAt,
		boolean current,
		int itemCount
	) {
	}

	record CandidateItemRecord(
		long placeId,
		int displayOrder,
		Long representativeImageId,
		String curatorMessageKo,
		String curatorMessageEn,
		List<String> displayTags,
		String editorNote,
		String titleKo,
		String titleEn,
		String imageUrl,
		ServiceRegionCode serviceRegionCode,
		String serviceRegionNameKo,
		String serviceRegionNameEn,
		TravelStyle travelStyle,
		CandidatePlaceReadiness readiness
	) {
		public CandidateSetItemDraft toDraft() {
			return new CandidateSetItemDraft(
				placeId,
				displayOrder,
				representativeImageId,
				curatorMessageKo,
				curatorMessageEn,
				displayTags,
				editorNote);
		}
	}

	record AuditRecord(
		String actorSubject,
		String action,
		String resourceId,
		CandidateSetStatus beforeStatus,
		CandidateSetStatus afterStatus,
		Instant createdAt
	) {
	}
}
