package koready_backend.horitip.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import koready_backend.horitip.domain.HoriTipPolicyException.Reason;
import koready_backend.place.domain.PlaceLanguage;

class HoriTipDraftTest {

	@Test
	void allowsAnIncompleteLanguageSetWhileDrafting() {
		assertDoesNotThrow(() -> draft(
			HoriTipPlacement.TOP_SUMMARY,
			allRoutes(),
			emptyTrigger(),
			List.of(translation(PlaceLanguage.KO)),
			Instant.parse("2026-07-20T00:00:00Z"),
			Instant.parse("2026-08-20T00:00:00Z")));
	}

	@Test
	void requiresKoreanAndEnglishBeforeActivation() {
		HoriTipDraft draft = draft(
			HoriTipPlacement.TOP_SUMMARY,
			allRoutes(),
			emptyTrigger(),
			List.of(translation(PlaceLanguage.KO)),
			null,
			null);

		HoriTipPolicyException exception = assertThrows(
			HoriTipPolicyException.class,
			draft::requireActivatable);

		assertEquals(Reason.ACTIVATION_INVALID, exception.reason());
		assertDoesNotThrow(() -> draft(
			HoriTipPlacement.TOP_SUMMARY,
			allRoutes(),
			summaryTrigger(),
			List.of(
				translation(PlaceLanguage.KO),
				translation(PlaceLanguage.EN)),
			null,
			null).requireActivatable());
	}

	@Test
	void requiresAMatchingConditionBeforeActivatingAnAllRoutesTip() {
		HoriTipDraft draft = draft(
			HoriTipPlacement.TOP_SUMMARY,
			allRoutes(),
			emptyTrigger(),
			List.of(
				translation(PlaceLanguage.KO),
				translation(PlaceLanguage.EN)),
			null,
			null);

		HoriTipPolicyException exception = assertThrows(
			HoriTipPolicyException.class,
			draft::requireActivatable);

		assertEquals(Reason.ACTIVATION_INVALID, exception.reason());
	}

	@Test
	void rejectsInvalidScopeSegmentAndPeriodRules() {
		assertRuleInvalid(() -> new HoriTipScope(
			HoriTipScopeType.ALL_ROUTES,
			List.of(1L)));
		assertRuleInvalid(() -> new HoriTipScope(
			HoriTipScopeType.DESTINATION_PLACES,
			List.of()));
		assertRuleInvalid(() -> draft(
			HoriTipPlacement.AFTER_SEGMENT,
			allRoutes(),
			emptyTrigger(),
			List.of(translation(PlaceLanguage.KO)),
			null,
			null));
		assertRuleInvalid(() -> draft(
			HoriTipPlacement.TOP_SUMMARY,
			allRoutes(),
			emptyTrigger(),
			List.of(translation(PlaceLanguage.KO)),
			Instant.parse("2026-08-20T00:00:00Z"),
			Instant.parse("2026-07-20T00:00:00Z")));
	}

	@Test
	void acceptsAnAfterSegmentTipWithAStructuredSegmentCondition() {
		HoriTipTrigger trigger = new HoriTipTrigger(
			List.of(HoriTipRouteMode.TRAIN),
			List.of("KTX"),
			List.of(),
			List.of("Gimcheon"),
			3600,
			null,
			null);

		assertDoesNotThrow(() -> draft(
			HoriTipPlacement.AFTER_SEGMENT,
			new HoriTipScope(HoriTipScopeType.DESTINATION_PLACES, List.of(1L)),
			trigger,
			List.of(translation(PlaceLanguage.KO)),
			null,
			null));
	}

	private static HoriTipDraft draft(
		HoriTipPlacement placement,
		HoriTipScope scope,
		HoriTipTrigger trigger,
		List<HoriTipTranslation> translations,
		Instant validFrom,
		Instant validUntil
	) {
		return new HoriTipDraft(
			placement,
			100,
			scope,
			trigger,
			translations,
			validFrom,
			validUntil,
			"Operator note");
	}

	private static HoriTipScope allRoutes() {
		return new HoriTipScope(HoriTipScopeType.ALL_ROUTES, List.of());
	}

	private static HoriTipTrigger emptyTrigger() {
		return new HoriTipTrigger(
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			null,
			null,
			null);
	}

	private static HoriTipTrigger summaryTrigger() {
		return new HoriTipTrigger(
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			3600,
			null,
			null);
	}

	private static HoriTipTranslation translation(PlaceLanguage language) {
		return new HoriTipTranslation(language, language + " body");
	}

	private static void assertRuleInvalid(Runnable action) {
		HoriTipPolicyException exception = assertThrows(
			HoriTipPolicyException.class,
			action::run);
		assertEquals(Reason.RULE_INVALID, exception.reason());
	}
}
