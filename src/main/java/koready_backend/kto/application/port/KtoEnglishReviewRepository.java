package koready_backend.kto.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.domain.KtoEnglishReviewDecision;
import koready_backend.kto.domain.KtoEnglishReviewStatus;
import koready_backend.kto.domain.KtoEnglishSourceQuality;
import koready_backend.kto.domain.KtoEnglishSourceQualityWarning;

public interface KtoEnglishReviewRepository {

	List<ReviewSummaryRecord> findPage(ReviewCriteria criteria);

	Optional<ReviewDetailRecord> findBySourceRecordId(long sourceRecordId);

	ReviewDecisionRecord review(ReviewCommand command);

	record ReviewCriteria(
		KtoEnglishReviewStatus status,
		KtoEnglishSourceQuality quality,
		String search,
		Long beforeSourceRecordId,
		int limit
	) {
	}

	record ReviewSummaryRecord(
		long sourceRecordId,
		String sourceContentId,
		String sourceOldContentId,
		String sourceHash,
		long rawSnapshotId,
		String storageKey,
		Instant capturedAt,
		KtoEnglishSourceQuality sourceQuality,
		Set<KtoEnglishSourceQualityWarning> qualityWarnings,
		Instant qualityClassifiedAt,
		KtoEnglishReviewStatus status,
		int candidateCount,
		int decisionVersion,
		Long selectedPlaceId,
		Instant decidedAt
	) {
	}

	record ReviewDetailRecord(
		ReviewSummaryRecord summary,
		List<CandidateRecord> candidates,
		List<AuditRecord> audits
	) {
	}

	record CandidateRecord(
		long placeId,
		String titleKo,
		String address,
		String imageUrl,
		String matchMethod,
		double confidence,
		int candidateCount,
		int imageCandidateCount,
		int coordinateCandidateCount,
		boolean evidenceConflict,
		boolean selected
	) {
	}

	record AuditRecord(
		long auditId,
		KtoEnglishReviewStatus previousStatus,
		KtoEnglishReviewStatus newStatus,
		Long previousPlaceId,
		Long selectedPlaceId,
		String reviewedBy,
		String reason,
		int decisionVersion,
		Instant createdAt
	) {
	}

	record ReviewCommand(
		long sourceRecordId,
		KtoEnglishReviewDecision decision,
		Long selectedPlaceId,
		int expectedVersion,
		String reviewedBy,
		String reason,
		KtoEnglishPlaceItem source
	) {
	}

	record ReviewDecisionRecord(
		long sourceRecordId,
		KtoEnglishReviewStatus status,
		Long selectedPlaceId,
		int version,
		String reviewedBy,
		String reason,
		Instant decidedAt
	) {
	}
}
