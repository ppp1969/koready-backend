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

	@Test
	void removesATrailingKoreanAliasWithNestedParentheses() {
		assertEquals(
			"Sweet Park (Lotte Children's Food Experience Center)",
			EnglishPlaceTitleNormalizer.normalize(
				"Sweet Park (Lotte Children's Food Experience Center) "
					+ "(스위트파크(롯데어린이식품체험관))"));
		assertEquals(
			"Daedunsan Provincial Park (Geumsan Section)",
			EnglishPlaceTitleNormalizer.normalize(
				"Daedunsan Provincial Park (Geumsan Section) (대둔산도립공원 (금산))"));
	}

	@Test
	void preservesNestedEnglishParentheses() {
		assertEquals(
			"Museum (Seoul (Main Hall))",
			EnglishPlaceTitleNormalizer.normalize("Museum (Seoul (Main Hall))"));
	}
}
