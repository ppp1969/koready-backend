package koready_backend.kto.application.model;

public record KtoRelatedTourStorePageResult(
	int processedCount,
	boolean replayed
) {

	public KtoRelatedTourStorePageResult {
		if (processedCount < 0) {
			throw new IllegalArgumentException(
				"Related tour processed count is invalid");
		}
	}
}
