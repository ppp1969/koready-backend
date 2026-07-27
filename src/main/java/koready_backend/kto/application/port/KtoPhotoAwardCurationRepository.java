package koready_backend.kto.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KtoPhotoAwardCurationRepository {

	Optional<PhotoAwardRecord> findByContentId(String contentId);

	boolean placeExists(long placeId);

	void saveMapping(MappingRecord mapping);

	boolean removeMapping(String contentId);

	void recordAudit(AuditRecord audit);

	List<PhotoAwardRecord> findPage(PhotoAwardQuery query);

	record PhotoAwardQuery(
		String query,
		Boolean mapped,
		long startAfterId,
		int limit
	) {
	}

	record PhotoAwardRecord(
		long id,
		String contentId,
		String titleKo,
		String filmLocationKo,
		String keywordKo,
		String titleEn,
		String filmLocationEn,
		String keywordEn,
		String originalImageUrl,
		String thumbnailImageUrl,
		String copyrightType,
		Long mappedPlaceId,
		String mappedPlaceTitleKo,
		Integer displayOrder,
		String approvedBySubject,
		String approvalReason,
		Instant approvedAt,
		Instant sourceCapturedAt
	) {
	}

	record MappingRecord(
		long photoAwardId,
		String contentId,
		long placeId,
		int displayOrder,
		String actorSubject,
		String reason,
		Instant approvedAt
	) {
	}

	record AuditRecord(
		String actorSubject,
		String action,
		String contentId,
		String reason,
		Instant createdAt
	) {
	}
}
