package koready_backend.editorial.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import koready_backend.editorial.application.EditorialService;
import koready_backend.editorial.domain.EditorialJobPriority;
import koready_backend.editorial.domain.EditorialJobStatus;
import koready_backend.editorial.domain.EditorialTriggerType;

final class EditorialDtos {

	private EditorialDtos() {
	}

	static QueueResponse from(EditorialService.JobView job) {
		return new QueueResponse(
			job.jobId(), job.placeId(), job.status(), job.priority(),
			job.triggerType(), job.requestedAt(), job.created());
	}

	static CandidateListResponse from(EditorialService.CandidatePage page) {
		return new CandidateListResponse(
			page.items().stream().map(item -> new CandidateResponse(
				item.placeId(), item.titleKo(), item.titleEn(), item.region(),
				item.imageUrl(), item.hasKoreanOverview(), item.queueEligible(),
				item.sourceTrack(), item.hasTrustedEnglish(),
				item.curationPriority(), item.status(),
				item.active(), item.showFlag(), item.visible(), item.requestedAt())).toList(),
			page.nextCursor(), page.hasMore(), page.totalCount());
	}

	static JobListResponse from(EditorialService.JobPage page) {
		return new JobListResponse(
			page.items().stream().map(item -> new JobResponse(
				item.jobId(), item.placeId(), item.status(), item.priority(),
				item.triggerType(), item.attemptCount(), item.errorCode(),
				item.errorMessage(), item.requestedAt(), item.startedAt(),
				item.completedAt())).toList(),
			page.nextCursor(), page.hasMore());
	}

	static CandidateDetailResponse from(EditorialService.CandidateDetailView item) {
		return new CandidateDetailResponse(
			item.placeId(), item.titleKo(), item.titleEn(), item.overviewKo(),
			item.address(), item.region(), item.imageUrls(), item.images().stream()
				.map(image -> new ImageOrderItemResponse(
					image.imageId(), image.imageUrl(), image.displayOrder(), image.thumbnail()))
				.toList(), item.travelStyles(), item.curationPriority(),
			item.sourceTrack(), item.hasTrustedEnglish(),
			item.active(), item.showFlag(), item.visible(), item.status(), item.requestedAt());
	}

	static VisibilityResponse from(EditorialService.PlaceVisibilityView item) {
		return new VisibilityResponse(
			item.placeId(), item.active(), item.showFlag(), item.visible(), item.updatedAt());
	}

	static PriorityResponse from(EditorialService.PlacePriorityView item) {
		return new PriorityResponse(item.placeId(), item.priority(), item.updatedAt());
	}

	static ImageOrderResponse from(EditorialService.PlaceImageOrderView item) {
		return new ImageOrderResponse(
			item.placeId(),
			item.images().stream().map(image -> new ImageOrderItemResponse(
				image.imageId(), image.imageUrl(), image.displayOrder(), image.thumbnail()))
				.toList(),
			item.updatedAt());
	}

	record QueueResponse(
		String jobId,
		long placeId,
		EditorialJobStatus status,
		EditorialJobPriority priority,
		EditorialTriggerType triggerType,
		Instant requestedAt,
		boolean created
	) {
	}

	record CandidateListResponse(
		List<CandidateResponse> items,
		String nextCursor,
		boolean hasMore,
		long totalCount
	) {
	}

	record CandidateResponse(
		long placeId,
		String titleKo,
		String titleEn,
		String region,
		String imageUrl,
		boolean hasKoreanOverview,
		boolean queueEligible,
		koready_backend.editorial.domain.EditorialCandidateSourceTrack sourceTrack,
		boolean hasTrustedEnglish,
		int curationPriority,
		EditorialJobStatus editorialStatus,
		boolean active,
		boolean showFlag,
		boolean visible,
		Instant requestedAt
	) {
	}

	record CandidateDetailResponse(
		long placeId,
		String titleKo,
		String titleEn,
		String overviewKo,
		String address,
		String region,
		List<String> imageUrls,
		List<ImageOrderItemResponse> images,
		List<String> travelStyles,
		int curationPriority,
		koready_backend.editorial.domain.EditorialCandidateSourceTrack sourceTrack,
		boolean hasTrustedEnglish,
		boolean active,
		boolean showFlag,
		boolean visible,
		EditorialJobStatus editorialStatus,
		Instant requestedAt
	) {
	}

	record JobListResponse(
		List<JobResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record JobResponse(
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

	record VisibilityRequest(
		@jakarta.validation.constraints.NotNull Boolean visible
	) {
	}

	record VisibilityResponse(
		long placeId,
		boolean active,
		boolean showFlag,
		boolean visible,
		Instant updatedAt
	) {
	}

	record PriorityRequest(@NotNull @Min(0) @Max(1000) Integer priority) {
	}

	record PriorityResponse(long placeId, int priority, Instant updatedAt) {
	}

	record ImageOrderRequest(
		@NotNull @Size(min = 1, max = 100) List<@NotNull @Positive Long> imageIds
	) {
	}

	record ImageOrderResponse(
		long placeId,
		List<ImageOrderItemResponse> images,
		Instant updatedAt
	) {
	}

	record ImageOrderItemResponse(
		long imageId,
		String imageUrl,
		int displayOrder,
		boolean thumbnail
	) {
	}
}
