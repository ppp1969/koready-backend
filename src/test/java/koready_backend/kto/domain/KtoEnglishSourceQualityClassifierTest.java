package koready_backend.kto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KtoEnglishSourceQualityClassifierTest {

	private final KtoEnglishSourceQualityClassifier classifier =
		new KtoEnglishSourceQualityClassifier();

	@Test
	void acceptsUsableEnglishTitleAndAddress() {
		var result = classifier.classify(
			"Gyeongbokgung Palace",
			"161 Sajik-ro, Jongno-gu, Seoul");

		assertEquals(KtoEnglishSourceQuality.USABLE, result.quality());
		assertTrue(result.warnings().isEmpty());
	}

	@Test
	void detectsANameWrittenPrimarilyInCyrillic() {
		var result = classifier.classify(
			"Детский музей Самсунг",
			"Seoul");

		assertEquals(
			KtoEnglishSourceQuality.NON_ENGLISH_SUSPECTED,
			result.quality());
		assertTrue(result.warnings().contains(
			KtoEnglishSourceQualityWarning.NON_LATIN_TITLE));
	}

	@Test
	void detectsCommonUtf8MojibakeMarkers() {
		var result = classifier.classify(
			"FranÃ§ais CafÃ©",
			"Seoul");

		assertEquals(
			KtoEnglishSourceQuality.ENCODING_SUSPECTED,
			result.quality());
		assertTrue(result.warnings().contains(
			KtoEnglishSourceQualityWarning.ENCODING_MARKER));
	}

	@Test
	void marksMixedScriptsAsUnknownInsteadOfRejectingThem() {
		var result = classifier.classify(
			"Seoul Αθήνα Culture Center",
			"Seoul");

		assertEquals(
			KtoEnglishSourceQuality.MIXED_OR_UNKNOWN,
			result.quality());
		assertTrue(result.warnings().contains(
			KtoEnglishSourceQualityWarning.MIXED_SCRIPTS));
	}
}
