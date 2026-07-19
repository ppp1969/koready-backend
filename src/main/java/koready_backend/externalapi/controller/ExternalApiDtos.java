package koready_backend.externalapi.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import koready_backend.externalapi.application.ExternalApiAdminService;
import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.RawSnapshotStatus;
import koready_backend.externalapi.domain.SnapshotRetentionClass;
import koready_backend.externalapi.domain.SnapshotStorageFormat;
import koready_backend.externalapi.domain.SyncCursorType;

final class ExternalApiDtos {

	private ExternalApiDtos() {
	}

	static SummaryResponse from(ExternalApiAdminService.OpenApiSummary summary) {
		return new SummaryResponse(
			new PeriodResponse(summary.period().from(), summary.period().to()),
			summary.totalCalls(),
			summary.successCalls(),
			summary.failureCalls(),
			summary.successRate(),
			summary.rawSnapshotCount(),
			summary.providers().stream().map(provider -> new ProviderSummaryResponse(
				provider.provider(),
				provider.calls(),
				provider.success(),
				provider.failure(),
				provider.lastSuccessAt())).toList(),
			summary.recentFailures().stream().map(ExternalApiDtos::summary).toList());
	}

	static CallListResponse from(ExternalApiAdminService.CallPage page) {
		return new CallListResponse(
			page.items().stream().map(ExternalApiDtos::summary).toList(),
			page.nextCursor(),
			page.hasMore());
	}

	static CallResponse from(ExternalApiAdminService.CallView call) {
		return new CallResponse(
			call.callLogId(),
			call.provider(),
			call.apiName(),
			call.operation(),
			call.requestStartedAt(),
			call.responseReceivedAt(),
			call.durationMs(),
			call.success(),
			call.httpStatus(),
			call.externalResultCode(),
			call.itemCount(),
			call.responseBytes(),
			call.rawSnapshotStatus(),
			call.relatedJobId(),
			call.endpoint(),
			call.requestParamsMasked(),
			responseSummary(call.responseSummary()),
			call.error() == null ? null : new CallErrorResponse(
				call.error().code(), call.error().message()),
			call.relatedJob() == null ? null : new RelatedJobResponse(
				call.relatedJob().jobId(), call.relatedJob().jobType()),
			call.rawSnapshot() == null ? null : new RelatedSnapshotResponse(
				call.rawSnapshot().snapshotId(),
				call.rawSnapshot().status(),
				call.rawSnapshot().rawContentSha256(),
				call.rawSnapshot().storedObjectSha256(),
				call.rawSnapshot().byteSize(),
				call.rawSnapshot().downloadable()));
	}

	static SnapshotListResponse from(ExternalApiAdminService.SnapshotPage page) {
		return new SnapshotListResponse(
			page.items().stream().map(ExternalApiDtos::from).toList(),
			page.nextCursor(),
			page.hasMore());
	}

	static SnapshotResponse from(ExternalApiAdminService.SnapshotView snapshot) {
		return new SnapshotResponse(
			snapshot.snapshotId(),
			snapshot.callLogId(),
			snapshot.provider(),
			snapshot.apiName(),
			snapshot.operation(),
			snapshot.storageKey(),
			snapshot.storageFormat(),
			snapshot.contentType(),
			snapshot.rawContentSha256(),
			snapshot.storedObjectSha256(),
			snapshot.byteSize(),
			snapshot.compressedByteSize(),
			snapshot.itemCount(),
			snapshot.capturedAt(),
			snapshot.retentionClass(),
			snapshot.retentionUntil(),
			snapshot.immutable(),
			snapshot.downloadable());
	}

	static SyncCursorListResponse syncCursors(
		List<ExternalApiAdminService.SyncCursorView> cursors
	) {
		return new SyncCursorListResponse(cursors.stream()
			.map(cursor -> new SyncCursorResponse(
				cursor.cursorId(),
				cursor.provider(),
				cursor.apiName(),
				cursor.operation(),
				cursor.cursorType(),
				cursor.cursorValue(),
				cursor.lastSuccessAt(),
				cursor.lastFailureAt(),
				cursor.failureCount(),
				cursor.enabled(),
				cursor.createdAt(),
				cursor.updatedAt()))
			.toList());
	}

	private static CallSummaryResponse summary(ExternalApiAdminService.CallView call) {
		return new CallSummaryResponse(
			call.callLogId(),
			call.provider(),
			call.apiName(),
			call.operation(),
			call.requestStartedAt(),
			call.responseReceivedAt(),
			call.durationMs(),
			call.success(),
			call.httpStatus(),
			call.externalResultCode(),
			call.itemCount(),
			call.responseBytes(),
			call.rawSnapshotStatus(),
			call.relatedJobId());
	}

	private static ResponseSummaryResponse responseSummary(
		ExternalApiAdminService.ResponseSummary summary
	) {
		return summary == null ? null : new ResponseSummaryResponse(
			summary.resultCode(),
			summary.resultMessage(),
			summary.totalCount(),
			summary.itemCount());
	}

	record PeriodResponse(Instant from, Instant to) {
	}

	record SummaryResponse(
		PeriodResponse period,
		long totalCalls,
		long successCalls,
		long failureCalls,
		double successRate,
		long rawSnapshotCount,
		List<ProviderSummaryResponse> providers,
		List<CallSummaryResponse> recentFailures
	) {
	}

	record ProviderSummaryResponse(
		ExternalApiProvider provider,
		long calls,
		long success,
		long failure,
		Instant lastSuccessAt
	) {
	}

	record CallListResponse(
		List<CallSummaryResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record CallSummaryResponse(
		long callLogId,
		ExternalApiProvider provider,
		String apiName,
		String operation,
		Instant requestStartedAt,
		Instant responseReceivedAt,
		long durationMs,
		boolean success,
		Integer httpStatus,
		String externalResultCode,
		Integer itemCount,
		long responseBytes,
		RawSnapshotStatus rawSnapshotStatus,
		Long relatedJobId
	) {
	}

	record CallResponse(
		long callLogId,
		ExternalApiProvider provider,
		String apiName,
		String operation,
		Instant requestStartedAt,
		Instant responseReceivedAt,
		long durationMs,
		boolean success,
		Integer httpStatus,
		String externalResultCode,
		Integer itemCount,
		long responseBytes,
		RawSnapshotStatus rawSnapshotStatus,
		Long relatedJobId,
		String endpoint,
		Map<String, String> requestParamsMasked,
		ResponseSummaryResponse responseSummary,
		CallErrorResponse error,
		RelatedJobResponse relatedJob,
		RelatedSnapshotResponse rawSnapshot
	) {
	}

	record ResponseSummaryResponse(
		String resultCode,
		String resultMessage,
		Integer totalCount,
		Integer itemCount
	) {
	}

	record CallErrorResponse(String code, String message) {
	}

	record RelatedJobResponse(long jobId, String jobType) {
	}

	record RelatedSnapshotResponse(
		long snapshotId,
		RawSnapshotStatus status,
		String rawContentSha256,
		String storedObjectSha256,
		long byteSize,
		boolean downloadable
	) {
	}

	record SnapshotListResponse(
		List<SnapshotResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record SnapshotResponse(
		long snapshotId,
		long callLogId,
		ExternalApiProvider provider,
		String apiName,
		String operation,
		String storageKey,
		SnapshotStorageFormat storageFormat,
		String contentType,
		String rawContentSha256,
		String storedObjectSha256,
		long byteSize,
		long compressedByteSize,
		int itemCount,
		Instant capturedAt,
		SnapshotRetentionClass retentionClass,
		Instant retentionUntil,
		boolean immutable,
		boolean downloadable
	) {
	}

	record SyncCursorListResponse(List<SyncCursorResponse> items) {
	}

	record SyncCursorResponse(
		long cursorId,
		ExternalApiProvider provider,
		String apiName,
		String operation,
		SyncCursorType cursorType,
		String cursorValue,
		Instant lastSuccessAt,
		Instant lastFailureAt,
		int failureCount,
		boolean enabled,
		Instant createdAt,
		Instant updatedAt
	) {
	}
}
