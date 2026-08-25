package koready_backend.editorial.application;

import java.util.HashSet;

import org.springframework.stereotype.Component;

import koready_backend.editorial.domain.EditorialGeneration;

@Component
public class EditorialOutputValidator {

	public void validate(EditorialGeneration generation) {
		if (generation == null || generation.tags() == null
			|| generation.tags().size() != 2
			|| new HashSet<>(generation.tags()).size() != 2) {
			throw new IllegalArgumentException("Exactly two distinct tags are required");
		}
		validateLocalized(generation.korean());
		validateLocalized(generation.english());
		if (invalid(generation.titleEn(), 300) || invalid(generation.addressEn(), 500)) {
			throw new IllegalArgumentException("English place title and address are required");
		}
	}

	private void validateLocalized(EditorialGeneration.LocalizedContent content) {
		if (content == null
			|| invalid(content.topic(), 100)
			|| invalid(content.oneLineDescription(), 300)
			|| invalid(content.shortIntroduction(), 1000)
			|| content.enjoyPoints() == null
			|| content.enjoyPoints().size() < 3
			|| content.enjoyPoints().size() > 5
			|| content.enjoyPoints().stream().anyMatch(point -> invalid(point, 300))) {
			throw new IllegalArgumentException("Editorial output does not satisfy the content contract");
		}
	}

	private static boolean invalid(String value, int maxLength) {
		return value == null || value.isBlank() || value.length() > maxLength;
	}
}
