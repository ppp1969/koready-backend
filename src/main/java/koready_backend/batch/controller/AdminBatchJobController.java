package koready_backend.batch.controller;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.batch.application.BatchJobAdminService;
import koready_backend.batch.domain.BatchItemStatus;
import koready_backend.batch.domain.BatchItemTargetType;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@Validated
@RestController
@RequestMapping("/api/v1/admin/batch-jobs")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
public class AdminBatchJobController {

	private final BatchJobAdminService service;

	public AdminBatchJobController(BatchJobAdminService service) {
		this.service = service;
	}

	@GetMapping
	public ApiEnvelope<BatchJobDtos.BatchJobListResponse> list(
		@RequestParam(required = false) BatchJobType jobType,
		@RequestParam(required = false) BatchJobStatus status,
		@RequestParam(required = false) BatchTriggerSource triggerSource,
		@RequestParam(required = false) Instant from,
		@RequestParam(required = false) Instant to,
		@RequestParam(required = false) @Size(max = 512) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		var query = new BatchJobAdminService.JobQuery(
			jobType, status, triggerSource, from, to, cursor, size);
		return ApiEnvelope.success(
			"BATCH_JOB_LIST_OK",
			BatchJobDtos.from(service.listJobs(query)),
			TraceIdFilter.current(request));
	}

	@GetMapping("/{jobId}")
	public ApiEnvelope<BatchJobDtos.BatchJobResponse> get(
		@PathVariable @Positive long jobId,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"BATCH_JOB_OK",
			BatchJobDtos.from(service.getJob(jobId)),
			TraceIdFilter.current(request));
	}

	@GetMapping("/{jobId}/items")
	public ApiEnvelope<BatchJobDtos.BatchItemListResponse> items(
		@PathVariable @Positive long jobId,
		@RequestParam(required = false) BatchItemStatus status,
		@RequestParam(required = false) BatchItemTargetType targetType,
		@RequestParam(required = false) @Size(max = 512) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		var query = new BatchJobAdminService.ItemQuery(status, targetType, cursor, size);
		return ApiEnvelope.success(
			"BATCH_JOB_ITEM_LIST_OK",
			BatchJobDtos.from(service.listItems(jobId, query)),
			TraceIdFilter.current(request));
	}
}
