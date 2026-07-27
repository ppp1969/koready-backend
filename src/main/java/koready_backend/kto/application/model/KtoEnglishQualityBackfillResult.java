package koready_backend.kto.application.model;

public record KtoEnglishQualityBackfillResult(
	int processedRecords,
	long lastProcessedSourceRecordId,
	boolean hasMore,
	boolean autoContinue
) {
}
