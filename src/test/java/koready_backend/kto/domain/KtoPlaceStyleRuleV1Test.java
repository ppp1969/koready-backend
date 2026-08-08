package koready_backend.kto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

import koready_backend.place.domain.TravelStyle;

class KtoPlaceStyleRuleV1Test {

	private final KtoPlaceStyleRuleV1 rule = new KtoPlaceStyleRuleV1();

	@Test
	void classifiesApprovedFoodSubcategoriesOnly() {
		assertStyle("FD", "FD01", "FD010100", TravelStyle.LOCAL_FOOD);
		assertStyle("FD", "FD02", "FD020100", TravelStyle.LOCAL_FOOD);
		assertStyle("FD", "FD03", "FD030400", TravelStyle.LOCAL_FOOD);
		assertNoStyle("FD", "FD04", "FD040100");
		assertNoStyle("FD", "FD05", "FD050100");
	}

	@Test
	void classifiesFestivalsButNotPerformancesOrGeneralEvents() {
		assertStyle("EV", "EV01", "EV010100", TravelStyle.LOCAL_FESTIVAL);
		assertNoStyle("EV", "EV02", "EV020700");
		assertNoStyle("EV", "EV03", "EV030100");
	}

	@Test
	void classifiesTraditionalMarketsOnly() {
		assertStyle("SH", "SH06", "SH060100", TravelStyle.TRADITIONAL_MARKET);
		assertStyle("SH", "SH06", "SH060200", TravelStyle.TRADITIONAL_MARKET);
		assertNoStyle("SH", "SH01", "SH010100");
		assertNoStyle("SH", "SH05", "SH050300");
	}

	@Test
	void classifiesApprovedCulturalExperiencesOnly() {
		assertStyle("EX", "EX01", "EX010100", TravelStyle.CULTURE_EXPERIENCE);
		assertStyle("EX", "EX02", "EX020400", TravelStyle.CULTURE_EXPERIENCE);
		assertStyle("EX", "EX03", "EX030100", TravelStyle.CULTURE_EXPERIENCE);
		assertStyle("EX", "EX04", "EX040100", TravelStyle.CULTURE_EXPERIENCE);
		assertNoStyle("EX", "EX05", "EX050100");
		assertNoStyle("EX", "EX06", "EX060100");
		assertNoStyle("EX", "EX07", "EX070100");
	}

	@Test
	void classifiesEveryNaturalTourismSubcategory() {
		assertStyle("NA", "NA01", "NA010100", TravelStyle.NATURE);
		assertStyle("NA", "NA05", "NA050100", TravelStyle.NATURE);
	}

	@Test
	void classifiesApprovedExhibitionFacilitiesOnly() {
		assertStyle("VE", "VE07", "VE070100", TravelStyle.EXHIBITION_MUSEUM);
		assertStyle("VE", "VE07", "VE070300", TravelStyle.EXHIBITION_MUSEUM);
		assertStyle("VE", "VE07", "VE070500", TravelStyle.EXHIBITION_MUSEUM);
		assertStyle("VE", "VE07", "VE070600", TravelStyle.EXHIBITION_MUSEUM);
		assertNoStyle("VE", "VE07", "VE070200");
		assertNoStyle("VE", "VE07", "VE070400");
	}

	@Test
	void neverAutomaticallyClassifiesDramaLocations() {
		assertNoStyle("VE", "VE12", "VE120300");
		assertNoStyle(null, null, null);
	}

	@Test
	void doesNotUseBroadContentTypeAsFallbackWhenDetailedCodeIsMissing() {
		assertEquals(
			Set.of(),
			rule.classify(new KtoPlaceClassificationInput("39", null, null, null)));
		assertEquals(
			Set.of(),
			rule.classify(new KtoPlaceClassificationInput("15", null, null, null)));
		assertEquals(
			Set.of(),
			rule.classify(new KtoPlaceClassificationInput("38", null, null, null)));
	}

	private void assertStyle(
		String level1,
		String level2,
		String level3,
		TravelStyle expected
	) {
		assertEquals(
			Set.of(expected),
			rule.classify(new KtoPlaceClassificationInput(
				contentTypeFor(level1), level1, level2, level3)));
	}

	private void assertNoStyle(String level1, String level2, String level3) {
		assertEquals(
			Set.of(),
			rule.classify(new KtoPlaceClassificationInput(
				contentTypeFor(level1), level1, level2, level3)));
	}

	private String contentTypeFor(String level1) {
		if (level1 == null) {
			return null;
		}
		return switch (level1) {
			case "FD" -> "39";
			case "EV" -> "15";
			case "SH" -> "38";
			case "VE" -> "14";
			default -> "12";
		};
	}
}
