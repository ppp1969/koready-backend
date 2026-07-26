package koready_backend.kto.application.model;

public record KtoDetailEnrichmentResult(
	int processedPlaces,
	int successfulOperations,
	long lastProcessedPlaceId,
	boolean hasMore,
	boolean autoContinue
) {
}
