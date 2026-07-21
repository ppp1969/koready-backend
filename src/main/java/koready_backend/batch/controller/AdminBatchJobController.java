package koready_backend.batch.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.batch.application.BatchJobAdminService;
import koready_backend.batch.application.BatchJobCommandService;
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
	private final BatchJobCommandService commandService;

	public AdminBatchJobController(BatchJobAdminService service, BatchJobCommandService commandService) {
		this.service = service;
		this.commandService = commandService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
	@Operation(
		summary = "KTO 수집 작업 접수",
		description = "KTO_FULL_CATALOG_SYNC는 KTO 전체 장소 목록을 수집합니다. "
			+ "한 작업은 최대 20페이지를 처리하고, 성공한 경우에만 다음 페이지 범위를 자동으로 대기열에 넣습니다. "
			+ "KTO_DAILY_SYNC와 KTO_FESTIVAL_SYNC는 기존처럼 범위를 지정해 수동으로 실행합니다."
	)
	public ApiEnvelope<BatchJobDtos.BatchJobAcceptedResponse> create(
		@RequestBody @Validated BatchJobDtos.CreateBatchJobRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		var accepted = commandService.accept(new BatchJobCommandService.CreateCommand(
			body.jobType(), body.parameters(), body.reason(), authentication.getName()));
		return ApiEnvelope.success(
			"BATCH_JOB_ACCEPTED",
			BatchJobDtos.accepted(accepted),
			TraceIdFilter.current(request));
	}

	@PostMapping("/{jobId}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
	public ApiEnvelope<BatchJobDtos.BatchJobAcceptedResponse> retry(
		@PathVariable @Positive long jobId,
		@RequestBody @Validated BatchJobDtos.RetryBatchJobRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		var accepted = commandService.retry(jobId, new BatchJobCommandService.RetryCommand(
			body.scope(), body.reason(), authentication.getName()));
		return ApiEnvelope.success(
			"BATCH_JOB_RETRY_ACCEPTED",
			BatchJobDtos.accepted(accepted),
			TraceIdFilter.current(request));
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
