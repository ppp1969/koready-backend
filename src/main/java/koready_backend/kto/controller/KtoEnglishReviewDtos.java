package koready_backend.kto.controller;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.kto.application.KtoEnglishReviewService;
import koready_backend.kto.domain.KtoEnglishReviewDecision;
import koready_backend.kto.domain.KtoEnglishReviewStatus;
import koready_backend.kto.domain.KtoEnglishSourceQuality;
import koready_backend.kto.domain.KtoEnglishSourceQualityWarning;

final class KtoEnglishReviewDtos {

	private KtoEnglishReviewDtos() {
	}

	static ReviewListResponse from(KtoEnglishReviewService.ReviewPage page) {
		return new ReviewListResponse(
			page.items().stream().map(KtoEnglishReviewDtos::from).toList(),
			page.nextCursor(),
			page.hasMore());
	}

	static ReviewDetailResponse from(KtoEnglishReviewService.ReviewDetailView detail) {
		var source = detail.source();
		return new ReviewDetailResponse(
			from(detail.summary()),
			new SourceResponse(
				source.contentId(),
				source.oldContentId(),
				source.contentTypeId(),
				source.title(),
				source.address1(),
				source.address2(),
				source.primaryImageUrl(),
				source.thumbnailImageUrl(),
				source.longitude(),
				source.latitude(),
				source.modifiedTime(),
				source.showFlag(),
				source.sourceHash(),
				source.rawSnapshotId()),
			detail.candidates().stream().map(candidate -> new CandidateResponse(
				candidate.placeId(),
				candidate.titleKo(),
				candidate.address(),
				candidate.imageUrl(),
				candidate.matchMethod(),
				candidate.confidence(),
				candidate.candidateCount(),
				candidate.imageCandidateCount(),
				candidate.coordinateCandidateCount(),
				candidate.evidenceConflict(),
				candidate.selected())).toList(),
			detail.audits().stream().map(audit -> new AuditResponse(
				audit.auditId(),
				audit.previousStatus(),
				audit.newStatus(),
				audit.previousPlaceId(),
				audit.selectedPlaceId(),
				audit.reviewedBy(),
				audit.reason(),
				audit.decisionVersion(),
				audit.createdAt())).toList());
	}

	static DecisionResponse from(KtoEnglishReviewService.ReviewDecisionView decision) {
		return new DecisionResponse(
			decision.sourceRecordId(),
			decision.status(),
			decision.selectedPlaceId(),
			decision.version(),
			decision.reviewedBy(),
			decision.reason(),
			decision.decidedAt());
	}

	private static ReviewSummaryResponse from(
		KtoEnglishReviewService.ReviewSummaryView item
	) {
		return new ReviewSummaryResponse(
			item.sourceRecordId(),
			item.sourceContentId(),
			item.titleEn(),
			item.addressEn(),
			item.primaryImageUrl(),
			item.sourceAvailable(),
			item.sourceQuality(),
			item.qualityWarnings(),
			item.status(),
			item.candidateCount(),
			item.decisionVersion(),
			item.selectedPlaceId(),
			item.capturedAt(),
			item.decidedAt());
	}

	record ReviewListResponse(
		List<ReviewSummaryResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record ReviewSummaryResponse(
		long sourceRecordId,
		String sourceContentId,
		String titleEn,
		String addressEn,
		String primaryImageUrl,
		boolean sourceAvailable,
		KtoEnglishSourceQuality sourceQuality,
		Set<KtoEnglishSourceQualityWarning> qualityWarnings,
		KtoEnglishReviewStatus status,
		int candidateCount,
		int decisionVersion,
		Long selectedPlaceId,
		Instant capturedAt,
		Instant decidedAt
	) {
	}

	record ReviewDetailResponse(
		ReviewSummaryResponse summary,
		SourceResponse source,
		List<CandidateResponse> candidates,
		List<AuditResponse> audits
	) {
	}

	record SourceResponse(
		String contentId,
		String oldContentId,
		String contentTypeId,
		String title,
		String address1,
		String address2,
		String primaryImageUrl,
		String thumbnailImageUrl,
		String longitude,
		String latitude,
		String modifiedTime,
		String showFlag,
		String sourceHash,
		long rawSnapshotId
	) {
	}

	record CandidateResponse(
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

	record AuditResponse(
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

	record DecisionRequest(
		@NotNull KtoEnglishReviewDecision decision,
		@Positive Long selectedPlaceId,
		@Min(0) int expectedVersion,
		@NotBlank @Size(max = 500) String reason
	) {
	}

	record DecisionResponse(
		long sourceRecordId,
		KtoEnglishReviewStatus status,
		Long selectedPlaceId,
		int decisionVersion,
		String reviewedBy,
		String reason,
		Instant decidedAt
	) {
	}
}
