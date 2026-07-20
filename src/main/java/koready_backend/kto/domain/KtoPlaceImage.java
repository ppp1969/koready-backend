package koready_backend.kto.domain;

import java.util.Objects;

public record KtoPlaceImage(
	String originImageUrl,
	String thumbnailImageUrl,
	String imageName,
	String copyrightType,
	int sourceOrder
) {

	public KtoPlaceImage {
		originImageUrl = requiredHttpUrl(originImageUrl, "origin image URL");
		thumbnailImageUrl = optionalHttpUrl(thumbnailImageUrl, "thumbnail image URL");
		imageName = normalize(imageName);
		copyrightType = normalize(copyrightType);
		if (sourceOrder < 1) {
			throw new IllegalArgumentException("KTO image source order must be positive");
		}
	}

	private static String requiredHttpUrl(String value, String field) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException("KTO " + field + " is required");
		}
		return httpUrl(normalized, field);
	}

	private static String optionalHttpUrl(String value, String field) {
		String normalized = normalize(value);
		return normalized == null ? null : httpUrl(normalized, field);
	}

	private static String httpUrl(String value, String field) {
		if (!(value.startsWith("https://") || value.startsWith("http://")) || value.length() > 1000) {
			throw new IllegalArgumentException("KTO " + field + " is invalid");
		}
		return value;
	}

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
