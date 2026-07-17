package koready_backend.kto.application.model;

public record KtoStorePageResult(
	long callLogId,
	long snapshotId,
	int processedCount,
	int activeCount,
	int localizationCount,
	boolean replayed
) {

	public KtoStorePageResult {
		if (callLogId < 1 || snapshotId < 1) {
			throw new IllegalArgumentException("KTO stored result identifiers must be positive");
		}
		if (processedCount < 0
			|| activeCount < 0
			|| localizationCount < 0
			|| activeCount > processedCount
			|| localizationCount > processedCount) {
			throw new IllegalArgumentException("KTO stored result counts are invalid");
		}
	}
}
