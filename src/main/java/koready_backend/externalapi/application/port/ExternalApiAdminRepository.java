package koready_backend.externalapi.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.SnapshotRetentionClass;
import koready_backend.externalapi.domain.SnapshotStorageFormat;
import koready_backend.externalapi.domain.SyncCursorType;

public interface ExternalApiAdminRepository {

	SummaryAggregate summarize(SummaryCriteria criteria);

	List<CallRecord> findCallPage(CallCriteria criteria);

	Optional<CallRecord> findCallById(long callLogId);

	List<SnapshotRecord> findSnapshotPage(SnapshotCriteria criteria);

	Optional<SnapshotRecord> findSnapshotById(long snapshotId);

	List<SyncCursorRecord> findSyncCursors();

	Optional<SyncCursorRecord> findSyncCursorByIdForUpdate(long cursorId);

	SyncCursorRecord updateSyncCursorEnabled(
		long cursorId,
		boolean enabled,
		Instant updatedAt
	);

	SyncCursorRecord resetSyncCursor(
		long cursorId,
		String cursorValue,
		Instant updatedAt
	);

	void recordSyncCursorAudit(SyncCursorAuditRecord audit);

	void recordSnapshotDownloadAudit(SnapshotDownloadAuditRecord audit);

	record SummaryCriteria(
		Instant from,
		Instant to,
		ExternalApiProvider provider
	) {
	}

	record SummaryAggregate(
		long totalCalls,
		long successCalls,
		long failureCalls,
		long rawSnapshotCount,
		List<ProviderAggregate> providers,
		List<CallRecord> recentFailures
	) {
	}

	record ProviderAggregate(
		ExternalApiProvider provider,
		long calls,
		long success,
		long failure,
		Instant lastSuccessAt
	) {
	}

	record CallCriteria(
		ExternalApiProvider provider,
		String apiName,
		String operation,
		Boolean success,
		Integer httpStatus,
		Instant from,
		Instant to,
		Long relatedJobId,
		Boolean hasRawSnapshot,
		Long beforeId,
		int limit
	) {
	}

	record SnapshotCriteria(
		ExternalApiProvider provider,
		String operation,
		SnapshotRetentionClass retentionClass,
		Instant from,
		Instant to,
		Long beforeId,
		int limit
	) {
	}

	record CallRecord(
		long id,
		ExternalApiProvider provider,
		String apiName,
		String operation,
		String endpoint,
		Instant requestStartedAt,
		Instant responseReceivedAt,
		Long durationMs,
		boolean success,
		Integer httpStatus,
		Map<String, Object> requestParams,
		Map<String, Object> responseSummary,
		String externalResultCode,
		Integer itemCount,
		Long responseBytes,
		String errorMessage,
		Long relatedJobId,
		String relatedJobType,
		SnapshotRecord snapshot
	) {
	}

	record SnapshotRecord(
		long id,
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
		boolean immutable
	) {
	}

	record SyncCursorRecord(
		long id,
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

	record SyncCursorAuditRecord(
		String actorSubject,
		String action,
		long cursorId,
		String reason,
		Map<String, Object> beforeSummary,
		Map<String, Object> afterSummary,
		Instant occurredAt
	) {
	}

	record SnapshotDownloadAuditRecord(
		String actorSubject,
		long snapshotId,
		ExternalApiProvider provider,
		String operation,
		Instant expiresAt,
		String rawContentSha256,
		String storedObjectSha256,
		Instant occurredAt
	) {
	}
}
