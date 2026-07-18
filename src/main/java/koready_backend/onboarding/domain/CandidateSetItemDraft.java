package koready_backend.onboarding.domain;

import java.util.HashSet;
import java.util.List;

public record CandidateSetItemDraft(
	long placeId,
	int displayOrder,
	Long representativeImageId,
	String curatorMessageKo,
	String curatorMessageEn,
	List<String> displayTags,
	String editorNote
) {

	public CandidateSetItemDraft {
		if (placeId <= 0) {
			throw new IllegalArgumentException("Place ID must be positive");
		}
		if (displayOrder < 1 || displayOrder > 10) {
			throw new IllegalArgumentException("Display order must be between 1 and 10");
		}
		if (representativeImageId != null && representativeImageId <= 0) {
			throw new IllegalArgumentException("Representative image ID must be positive");
		}
		curatorMessageKo = requiredText(curatorMessageKo, 160, "Korean curator message");
		curatorMessageEn = optionalText(curatorMessageEn, 240, "English curator message");
		editorNote = optionalText(editorNote, 500, "Editor note");
		displayTags = normalizeTags(displayTags);
	}

	private static List<String> normalizeTags(List<String> tags) {
		if (tags == null) {
			throw new IllegalArgumentException("Display tags are required");
		}
		if (tags.size() > 5) {
			throw new IllegalArgumentException("At most five display tags are allowed");
		}
		List<String> normalized = tags.stream()
			.map(tag -> requiredText(tag, 30, "Display tag"))
			.toList();
		if (new HashSet<>(normalized).size() != normalized.size()) {
			throw new IllegalArgumentException("Display tags must be unique");
		}
		return normalized;
	}

	private static String requiredText(String value, int maxLength, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(field + " is too long");
		}
		return normalized;
	}

	private static String optionalText(String value, int maxLength, String field) {
		if (value == null) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.isEmpty()) {
			return null;
		}
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(field + " is too long");
		}
		return normalized;
	}
}
