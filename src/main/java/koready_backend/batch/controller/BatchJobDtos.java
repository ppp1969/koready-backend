package koready_backend.batch.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import koready_backend.batch.application.BatchJobAdminService;
import koready_backend.batch.application.BatchJobCommandService;
import koready_backend.batch.domain.BatchItemStatus;
import koready_backend.batch.domain.BatchItemTargetType;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

final class BatchJobDtos {

	private BatchJobDtos() {
	}

	static BatchJobListResponse from(BatchJobAdminService.JobPage page) {
		return new BatchJobListResponse(
			page.items().stream().map(BatchJobDtos::from).toList(),
			page.nextCursor(),
			page.hasMore());
	}

	static BatchJobResponse from(BatchJobAdminService.BatchJobView job) {
		return new BatchJobResponse(
			job.jobId(),
			job.jobType(),
			job.status(),
			job.triggerSource(),
			job.startedAt(),
			job.finishedAt(),
			job.processedCount(),
			job.successCount(),
			job.failureCount(),
			job.message(),
			job.triggeredByUserId(),
			job.parentJobId(),
			job.parameters(),
			job.createdAt(),
			job.updatedAt());
	}

	static BatchItemListResponse from(BatchJobAdminService.ItemPage page) {
		return new BatchItemListResponse(
			page.jobId(),
			page.items().stream().map(BatchJobDtos::from).toList(),
			page.nextCursor(),
			page.hasMore());
	}

	static BatchJobAcceptedResponse accepted(BatchJobCommandService.JobAcceptance job) {
		return new BatchJobAcceptedResponse(
			job.jobId(), job.jobType(), job.status(), job.triggerSource(), job.originalJobId());
	}

	private static BatchItemResponse from(BatchJobAdminService.BatchItemView item) {
		return new BatchItemResponse(
			item.itemId(),
			item.targetType(),
			item.targetId(),
			item.status(),
			item.errorMessage(),
			item.createdAt(),
			item.updatedAt());
	}

	record BatchJobListResponse(
		List<BatchJobResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record CreateBatchJobRequest(
		BatchJobType jobType,
		Map<String, Object> parameters,
		@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason
	) {
	}

	record BatchJobAcceptedResponse(
		long jobId,
		BatchJobType jobType,
		BatchJobStatus status,
		BatchTriggerSource triggerSource,
		Long originalJobId
	) {
	}

	record RetryBatchJobRequest(
		@jakarta.validation.constraints.NotBlank String scope,
		@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason
	) {
	}

	record BatchJobResponse(
		long jobId,
		BatchJobType jobType,
		BatchJobStatus status,
		BatchTriggerSource triggerSource,
		Instant startedAt,
		Instant finishedAt,
		int processedCount,
		int successCount,
		int failureCount,
		String message,
		Long requestedByUserId,
		Long originalJobId,
		Map<String, Object> parameters,
		Instant createdAt,
		Instant updatedAt
	) {
	}

	record BatchItemListResponse(
		long jobId,
		List<BatchItemResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record BatchItemResponse(
		long itemId,
		BatchItemTargetType targetType,
		String targetId,
		BatchItemStatus status,
		String errorMessage,
		Instant createdAt,
		Instant updatedAt
	) {
	}
}
