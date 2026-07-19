package koready_backend.evidence.application.port;

import java.time.Instant;
import java.util.List;

import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.SnapshotRetentionClass;
import koready_backend.externalapi.domain.SnapshotStorageFormat;
import koready_backend.externalapi.domain.SyncCursorType;

public interface EvidenceBundleContentSource {

	List<CallRow> findCalls(Selection selection, long afterId, int limit);

	List<BatchJobRow> findBatchJobs(Instant from, Instant to, long afterId, int limit);

	List<SyncCursorRow> findSyncCursors(Selection selection);

	DataQualityRow dataQuality();

	record Selection(Instant from, Instant to, List<ExternalApiProvider> providers, List<String> operations) {
	}

	record CallRow(
		long id, ExternalApiProvider provider, String apiName, String operation,
		Instant requestStartedAt, Instant responseReceivedAt, Long durationMs, boolean success,
		Integer httpStatus, String externalResultCode, Integer itemCount, Long responseBytes,
		SnapshotRow snapshot
	) {
	}

	record SnapshotRow(
		long snapshotId, String storageKey, SnapshotStorageFormat storageFormat,
		String rawContentSha256, String storedObjectSha256, long byteSize,
		Instant capturedAt, SnapshotRetentionClass retentionClass, Instant retentionUntil
	) {
	}

	record BatchJobRow(
		long id, String jobType, String status, Instant startedAt, Instant finishedAt,
		int processedCount, int successCount, int failureCount, Instant createdAt
	) {
	}

	record SyncCursorRow(
		long id, ExternalApiProvider provider, String apiName, String operation,
		SyncCursorType cursorType, String cursorValue, boolean enabled,
		Instant lastSuccessAt, Instant lastFailureAt, int failureCount, Instant updatedAt
	) {
	}

	record DataQualityRow(long totalPlaces, long activePlaces, long curationReadyPlaces) {
	}
}
