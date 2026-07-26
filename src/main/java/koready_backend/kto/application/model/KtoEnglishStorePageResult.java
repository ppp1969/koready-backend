package koready_backend.kto.application.model;

public record KtoEnglishStorePageResult(
	long callLogId,
	long snapshotId,
	int processedCount,
	int autoMatchedCount,
	int reviewRequiredCount,
	int unmatchedCount,
	int localizedCount,
	boolean replayed
) {

	public KtoEnglishStorePageResult {
		if (callLogId < 1 || snapshotId < 1 || processedCount < 0
			|| autoMatchedCount < 0 || reviewRequiredCount < 0
			|| unmatchedCount < 0 || localizedCount < 0
			|| autoMatchedCount + reviewRequiredCount + unmatchedCount != processedCount
			|| localizedCount > autoMatchedCount) {
			throw new IllegalArgumentException("KTO English stored result is invalid");
		}
	}
}
