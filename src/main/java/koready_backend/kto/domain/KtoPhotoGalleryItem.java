package koready_backend.kto.domain;

import java.net.URI;

public record KtoPhotoGalleryItem(
	String contentId,
	String contentTypeId,
	String title,
	String photographyLocation,
	String photographyMonth,
	String photographer,
	String searchKeyword,
	String imageUrl,
	String createdTime,
	String modifiedTime,
	String sourceHash
) {

	public KtoPhotoGalleryItem {
		contentId = required(contentId, 100, "content ID");
		contentTypeId = optional(contentTypeId, 30);
		title = required(title, 500, "title");
		photographyLocation = optional(photographyLocation, 500);
		photographyMonth = optional(photographyMonth, 30);
		photographer = optional(photographer, 300);
		searchKeyword = optional(searchKeyword, 1000);
		imageUrl = requiredHttpUrl(imageUrl);
		createdTime = optional(createdTime, 30);
		modifiedTime = optional(modifiedTime, 30);
		if (sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException(
				"Photo gallery source hash is invalid");
		}
	}

	private static String required(
		String value,
		int maxLength,
		String field
	) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
				"Photo gallery " + field + " is required");
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(
				"Photo gallery " + field + " is too long");
		}
		return normalized;
	}

	private static String optional(String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(
				"Photo gallery field is too long");
		}
		return normalized;
	}

	private static String requiredHttpUrl(String value) {
		String normalized = required(value, 1000, "image");
		try {
			URI uri = URI.create(normalized);
			if (("https".equalsIgnoreCase(uri.getScheme())
				|| "http".equalsIgnoreCase(uri.getScheme()))
				&& uri.getHost() != null) {
				return normalized;
			}
		} catch (IllegalArgumentException ignored) {
			// Handled by the common validation error below.
		}
		throw new IllegalArgumentException(
			"Photo gallery image URL is invalid");
	}
}
