package koready_backend.kto.application.model;

public record KtoRelatedTourImportResult(
	int processedRegions,
	int processedPages,
	int processedItems,
	int replayedPages,
	String lastProcessedRegionKey,
	boolean hasMore,
	boolean autoContinue
) {
}
