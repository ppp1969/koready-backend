package koready_backend.kto.application.model;

public record KtoEnglishSyncResult(
	int processedPages,
	int processedItems,
	int autoMatchedItems,
	int reviewRequiredItems,
	int unmatchedItems,
	int localizedItems,
	int replayedPages,
	int reportedTotalCount,
	int lastProcessedPage,
	boolean truncatedByPageLimit
) {
}
