package koready_backend.kto.domain;

import java.net.URI;

public record KtoPhotoAwardItem(
	String contentId,
	String titleKo,
	String filmLocationKo,
	String keywordKo,
	String titleEn,
	String filmLocationEn,
	String keywordEn,
	String originalImageUrl,
	String thumbnailImageUrl,
	String copyrightType,
	String sourceHash
) {

	public KtoPhotoAwardItem {
		contentId = required(contentId, 100, "Photo award content ID");
		titleKo = required(titleKo, 500, "Photo award Korean title");
		filmLocationKo = optional(filmLocationKo, 500);
		keywordKo = optional(keywordKo, 1000);
		titleEn = optional(titleEn, 500);
		filmLocationEn = optional(filmLocationEn, 500);
		keywordEn = optional(keywordEn, 1000);
		originalImageUrl = requiredHttpUrl(
			originalImageUrl, "Photo award original image");
		thumbnailImageUrl = optionalHttpUrl(thumbnailImageUrl);
		copyrightType = optional(copyrightType, 30);
		if (sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("Photo award source hash is invalid");
		}
	}

	private static String required(String value, int maxLength, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(field + " is too long");
		}
		return normalized;
	}

	private static String optional(String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException("Photo award field is too long");
		}
		return normalized;
	}

	private static String requiredHttpUrl(String value, String field) {
		String normalized = required(value, 1000, field);
		if (!isHttpUrl(normalized)) {
			throw new IllegalArgumentException(field + " URL is invalid");
		}
		return normalized;
	}

	private static String optionalHttpUrl(String value) {
		String normalized = optional(value, 1000);
		if (normalized != null && !isHttpUrl(normalized)) {
			throw new IllegalArgumentException("Photo award thumbnail URL is invalid");
		}
		return normalized;
	}

	private static boolean isHttpUrl(String value) {
		try {
			URI uri = URI.create(value);
			return ("https".equalsIgnoreCase(uri.getScheme())
				|| "http".equalsIgnoreCase(uri.getScheme()))
				&& uri.getHost() != null;
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
}
