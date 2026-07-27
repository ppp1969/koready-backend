package koready_backend.kto.application.model;

public record KtoPhotoGalleryStorePageResult(
	long callLogId,
	long snapshotId,
	int processedCount,
	boolean replayed
) {
}
