package koready_backend.externalapi.controller;

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
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.externalapi.application.ExternalApiAdminService;
import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.SnapshotRetentionClass;

@Validated
@RestController
@RequestMapping("/api/v1/admin/open-api")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
public class AdminExternalApiController {

	private final ExternalApiAdminService service;

	public AdminExternalApiController(ExternalApiAdminService service) {
		this.service = service;
	}

	@GetMapping("/summary")
	public ApiEnvelope<ExternalApiDtos.SummaryResponse> summary(
		@RequestParam(required = false) Instant from,
		@RequestParam(required = false) Instant to,
		@RequestParam(required = false) ExternalApiProvider provider,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"OPEN_API_SUMMARY_OK",
			ExternalApiDtos.from(service.summary(from, to, provider)),
			TraceIdFilter.current(request));
	}

	@GetMapping("/calls")
	public ApiEnvelope<ExternalApiDtos.CallListResponse> calls(
		@RequestParam(required = false) ExternalApiProvider provider,
		@RequestParam(required = false) @Size(max = 100) String apiName,
		@RequestParam(required = false) @Size(max = 100) String operation,
		@RequestParam(required = false) Boolean success,
		@RequestParam(required = false) @Min(100) @Max(599) Integer httpStatus,
		@RequestParam(required = false) Instant from,
		@RequestParam(required = false) Instant to,
		@RequestParam(required = false) @Positive Long relatedJobId,
		@RequestParam(required = false) Boolean hasRawSnapshot,
		@RequestParam(required = false) @Size(max = 512) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		var query = new ExternalApiAdminService.CallQuery(
			provider,
			apiName,
			operation,
			success,
			httpStatus,
			from,
			to,
			relatedJobId,
			hasRawSnapshot,
			cursor,
			size);
		return ApiEnvelope.success(
			"OPEN_API_CALL_LIST_OK",
			ExternalApiDtos.from(service.listCalls(query)),
			TraceIdFilter.current(request));
	}

	@GetMapping("/calls/{callLogId}")
	public ApiEnvelope<ExternalApiDtos.CallResponse> call(
		@PathVariable @Positive long callLogId,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"OPEN_API_CALL_OK",
			ExternalApiDtos.from(service.getCall(callLogId)),
			TraceIdFilter.current(request));
	}

	@GetMapping("/snapshots")
	public ApiEnvelope<ExternalApiDtos.SnapshotListResponse> snapshots(
		@RequestParam(required = false) ExternalApiProvider provider,
		@RequestParam(required = false) @Size(max = 100) String operation,
		@RequestParam(required = false) SnapshotRetentionClass retentionClass,
		@RequestParam(required = false) Instant from,
		@RequestParam(required = false) Instant to,
		@RequestParam(required = false) @Size(max = 512) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		var query = new ExternalApiAdminService.SnapshotQuery(
			provider, operation, retentionClass, from, to, cursor, size);
		return ApiEnvelope.success(
			"RAW_SNAPSHOT_LIST_OK",
			ExternalApiDtos.from(service.listSnapshots(query)),
			TraceIdFilter.current(request));
	}

	@GetMapping("/snapshots/{snapshotId}")
	public ApiEnvelope<ExternalApiDtos.SnapshotResponse> snapshot(
		@PathVariable @Positive long snapshotId,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"RAW_SNAPSHOT_OK",
			ExternalApiDtos.from(service.getSnapshot(snapshotId)),
			TraceIdFilter.current(request));
	}
}
