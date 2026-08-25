package koready_backend.editorial.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import koready_backend.editorial.domain.TourismPurposeTag;
import koready_backend.editorial.infrastructure.ai.SpringAiEditorialGenerator.AiLocalizedContent;
import koready_backend.editorial.infrastructure.ai.SpringAiEditorialGenerator.AiResponse;

class SpringAiEditorialGeneratorTest {

	@Test
	void recordsGoogleGenAiProviderAndConfiguredModel() {
		var localized = new AiLocalizedContent(
			"Subject", "One-line description", "Short introduction",
			List.of("First activity", "Second activity", "Third activity"));
		var response = new AiResponse(
			localized, localized, "Gimcheon Gimbap Festival", "Gimcheon-si, Gyeongsangbuk-do",
			List.of(TourismPurposeTag.LOCAL, TourismPurposeTag.EXPERIENCE));

		var generation = SpringAiEditorialGenerator.toGeneration(
			response, "gemini-2.5-flash-lite");

		assertEquals("google-genai", generation.provider());
		assertEquals("gemini-2.5-flash-lite", generation.model());
		assertEquals("Gimcheon Gimbap Festival", generation.titleEn());
		assertEquals("Gimcheon-si, Gyeongsangbuk-do", generation.addressEn());
	}
}
