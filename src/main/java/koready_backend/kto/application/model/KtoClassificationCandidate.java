package koready_backend.kto.application.model;

import java.util.Set;

import koready_backend.place.domain.TravelStyle;

public record KtoClassificationCandidate(
	long placeId,
	String ktoContentId,
	String title,
	String contentTypeId,
	String classificationCode1,
	String classificationCode2,
	String classificationCode3,
	boolean hasImage,
	boolean currentlyPublished,
	Set<TravelStyle> manualStyles
) {

	public KtoClassificationCandidate {
		if (placeId <= 0) {
			throw new IllegalArgumentException("Place id must be positive");
		}
		if (ktoContentId == null || ktoContentId.isBlank()) {
			throw new IllegalArgumentException("KTO content id is required");
		}
		manualStyles = manualStyles == null ? Set.of() : Set.copyOf(manualStyles);
	}
}
