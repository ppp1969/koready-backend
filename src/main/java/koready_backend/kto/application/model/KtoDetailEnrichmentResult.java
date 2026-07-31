package koready_backend.kto.application.model;

public record KtoDetailEnrichmentResult(
	int processedPlaces,
	int successfulPlaces,
	int failedPlaces,
	int successfulOperations,
	long lastProcessedPlaceId,
	boolean hasMore,
	boolean autoContinue,
	boolean continuationAllowed
) {

	public KtoDetailEnrichmentResult {
		if (processedPlaces < 0
			|| successfulPlaces < 0
			|| failedPlaces < 0
			|| successfulPlaces + failedPlaces != processedPlaces
			|| successfulOperations < 0
			|| lastProcessedPlaceId < 0) {
			throw new IllegalArgumentException(
				"KTO detail enrichment result is invalid");
		}
	}
}
