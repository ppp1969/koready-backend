package koready_backend.kto.domain;

import java.util.Objects;

public record KtoDetailTarget(
	long placeId,
	String contentId,
	String contentTypeId
) {

	public KtoDetailTarget {
		if (placeId <= 0) {
			throw new IllegalArgumentException("KTO detail place ID must be positive");
		}
		contentId = required(contentId, "KTO detail content ID");
		contentTypeId = required(contentTypeId, "KTO detail content type ID");
	}

	private static String required(String value, String field) {
		String normalized = Objects.requireNonNull(value, field + " is required").strip();
		if (normalized.isEmpty() || normalized.length() > 100) {
			throw new IllegalArgumentException(field + " is invalid");
		}
		return normalized;
	}
}
