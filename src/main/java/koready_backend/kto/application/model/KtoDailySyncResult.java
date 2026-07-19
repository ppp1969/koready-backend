package koready_backend.kto.application.model;

public record KtoDailySyncResult(
	int processedPages,
	int processedItems,
	int replayedPages,
	int reportedTotalCount,
	int lastProcessedPage,
	boolean truncatedByPageLimit
) {
}
