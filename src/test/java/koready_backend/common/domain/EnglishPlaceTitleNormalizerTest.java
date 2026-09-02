package koready_backend.common.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnglishPlaceTitleNormalizerTest {
	@Test
	void removesOnlyATrailingKoreanAlias() {
		assertEquals("Gwangjang Market", EnglishPlaceTitleNormalizer.normalize("Gwangjang Market (광장시장)"));
		assertEquals("Hall (East Wing)", EnglishPlaceTitleNormalizer.normalize("Hall (East Wing)"));
		assertEquals("경복궁", EnglishPlaceTitleNormalizer.normalize("경복궁"));
	}

	@Test
	void supportsAttachedAndSpacedAliases() {
		assertEquals("Shop [Tax Refund Shop]", EnglishPlaceTitleNormalizer.normalize("Shop [Tax Refund Shop](상점)"));
	}
}
