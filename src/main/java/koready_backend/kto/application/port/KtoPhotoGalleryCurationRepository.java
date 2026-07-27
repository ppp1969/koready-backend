package koready_backend.kto.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KtoPhotoGalleryCurationRepository {

	Optional<PhotoGalleryRecord> findByContentId(String contentId);

	boolean placeExists(long placeId);

	void saveMapping(MappingRecord mapping);

	boolean removeMapping(String contentId);

	void recordAudit(AuditRecord audit);

	List<PhotoGalleryRecord> findPage(PhotoGalleryQuery query);

	record PhotoGalleryQuery(
		String query,
		Boolean mapped,
		long startAfterId,
		int limit
	) {
	}

	record PhotoGalleryRecord(
		long id,
		String contentId,
		String contentTypeId,
		String title,
		String photographyLocation,
		String photographyMonth,
		String photographer,
		String searchKeyword,
		String imageUrl,
		String rightsStatus,
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
		long photoGalleryId,
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
