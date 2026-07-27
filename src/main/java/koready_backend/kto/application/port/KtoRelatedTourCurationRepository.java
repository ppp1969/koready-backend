package koready_backend.kto.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KtoRelatedTourCurationRepository {

	Optional<RelatedTourRecord> findById(long recordId);

	boolean placeExists(long placeId);

	void saveMapping(MappingRecord mapping);

	boolean removeMapping(long recordId);

	void recordAudit(AuditRecord audit);

	List<RelatedTourRecord> findPage(RelatedTourQuery query);

	record RelatedTourQuery(
		String query,
		String matchStatus,
		long startAfterId,
		int limit
	) {
	}

	record RelatedTourRecord(
		long id,
		String baseYearMonth,
		String sourceTourCode,
		String sourceName,
		String sourceRegionName,
		String sourceSignguName,
		String relatedTourCode,
		String relatedName,
		String relatedRegionName,
		String relatedSignguName,
		String categoryLarge,
		String categoryMedium,
		String categorySmall,
		int rank,
		String matchStatus,
		Long sourcePlaceId,
		String sourcePlaceTitle,
		Long relatedPlaceId,
		String relatedPlaceTitle,
		String confirmedBySubject,
		String confirmationReason,
		Instant confirmedAt,
		Instant sourceCapturedAt
	) {
	}

	record MappingRecord(
		long recordId,
		long sourcePlaceId,
		long relatedPlaceId,
		String actorSubject,
		String reason,
		Instant confirmedAt
	) {
	}

	record AuditRecord(
		String actorSubject,
		String action,
		long recordId,
		String reason,
		Instant createdAt
	) {
	}
}
