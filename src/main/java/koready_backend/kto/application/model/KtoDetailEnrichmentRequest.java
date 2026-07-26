package koready_backend.kto.application.model;

public record KtoDetailEnrichmentRequest(
	long startAfterPlaceId,
	int maxPlaces,
	boolean autoContinue
) {

	public KtoDetailEnrichmentRequest {
		if (startAfterPlaceId < 0) {
			throw new IllegalArgumentException("KTO detail cursor must not be negative");
		}
		if (maxPlaces < 1 || maxPlaces > 50) {
			throw new IllegalArgumentException("KTO detail place limit must be between 1 and 50");
		}
	}
}
