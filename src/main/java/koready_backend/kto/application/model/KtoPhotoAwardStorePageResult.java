package koready_backend.kto.application.model;

public record KtoPhotoAwardStorePageResult(
	long callLogId,
	long snapshotId,
	int processedCount,
	boolean replayed
) {
}
