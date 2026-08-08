package koready_backend.kto.application.model;

import java.util.Set;

import koready_backend.place.domain.TravelStyle;

public record KtoClassificationDecision(
	long placeId,
	String contentTypeId,
	String classificationCode1,
	String classificationCode2,
	String classificationCode3,
	Set<TravelStyle> styles
) {

	public KtoClassificationDecision {
		if (placeId <= 0) {
			throw new IllegalArgumentException("Place id must be positive");
		}
		styles = styles == null ? Set.of() : Set.copyOf(styles);
	}
}
