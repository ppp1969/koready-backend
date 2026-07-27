package koready_backend.kto.application.model;

public record KtoPhotoGalleryImportResult(
	int startPage,
	int processedPages,
	int processedItems,
	int replayedPages,
	int reportedTotalCount,
	int lastProcessedPage,
	boolean truncatedByPageLimit
) {
}
