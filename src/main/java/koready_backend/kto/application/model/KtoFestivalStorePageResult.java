package koready_backend.kto.application.model;

public record KtoFestivalStorePageResult(
	long callLogId,
	long snapshotId,
	int processedCount,
	int occurrenceCount,
	boolean replayed
) {

	public KtoFestivalStorePageResult {
		if (callLogId < 1 || snapshotId < 1) {
			throw new IllegalArgumentException("KTO festival storage identifiers must be positive");
		}
		if (processedCount < 0 || occurrenceCount < 0 || occurrenceCount > processedCount) {
			throw new IllegalArgumentException("KTO festival storage counts are invalid");
		}
	}
}
