package koready_backend.onboarding.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

public record InitialCandidatePlace(
	int displayOrder,
	String searchKeyword,
	String ktoContentId,
	String ktoContentTypeId,
	String expectedKtoTitleKo,
	String titleEn,
	ServiceRegionCode serviceRegionCode,
	TravelStyle travelStyle,
	List<String> displayTags,
	String curatorMessageKo,
	String curatorMessageEn,
	String editorNote
) {

	public InitialCandidatePlace {
		if (displayOrder < 1 || displayOrder > 10) {
			throw new IllegalArgumentException("Initial candidate display order must be between 1 and 10");
		}
		searchKeyword = required(searchKeyword, 100, "Search keyword");
		ktoContentId = required(ktoContentId, 100, "KTO content ID");
		ktoContentTypeId = required(ktoContentTypeId, 30, "KTO content type ID");
		expectedKtoTitleKo = required(expectedKtoTitleKo, 300, "Expected KTO title");
		titleEn = required(titleEn, 300, "English title");
		serviceRegionCode = Objects.requireNonNull(serviceRegionCode, "Service region is required");
		travelStyle = Objects.requireNonNull(travelStyle, "Travel style is required");
		displayTags = normalizeTags(displayTags);
		curatorMessageKo = required(curatorMessageKo, 160, "Korean curator message");
		curatorMessageEn = required(curatorMessageEn, 240, "English curator message");
		editorNote = optional(editorNote, 500, "Editor note");
	}

	private static List<String> normalizeTags(List<String> tags) {
		if (tags == null || tags.isEmpty() || tags.size() > 5) {
			throw new IllegalArgumentException("Initial candidate requires between one and five tags");
		}
		List<String> normalized = tags.stream()
			.map(tag -> required(tag, 30, "Display tag"))
			.toList();
		if (new HashSet<>(normalized).size() != normalized.size()) {
			throw new IllegalArgumentException("Initial candidate tags must be unique");
		}
		return normalized;
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

	private static String optional(String value, int maxLength, String field) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return required(value, maxLength, field);
	}
}
