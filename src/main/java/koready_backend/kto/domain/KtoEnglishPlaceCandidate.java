package koready_backend.kto.domain;

public record KtoEnglishPlaceCandidate(
	long placeId,
	String imagePathHash,
	String coordinateContentTypeKey
) {

	public KtoEnglishPlaceCandidate {
		if (placeId < 1) {
			throw new IllegalArgumentException("KTO English match candidate place id must be positive");
		}
	}
}
