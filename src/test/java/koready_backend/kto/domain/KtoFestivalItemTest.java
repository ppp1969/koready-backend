package koready_backend.kto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class KtoFestivalItemTest {

	@Test
	void derivesTheOccurrenceYearAndSixMonthVisibilityWindow() {
		KtoFestivalItem item = new KtoFestivalItem(
			place("700001", "Festival"),
			LocalDate.of(2026, 10, 16),
			LocalDate.of(2026, 10, 18),
			null,
			null);

		assertEquals(2026, item.eventYear());
		assertEquals(LocalDate.of(2026, 4, 16), item.visibleFrom());
	}

	@Test
	void rejectsAnOccurrenceThatEndsBeforeItStarts() {
		KtoPlaceItem place = place("700001", "Festival");

		assertThrows(IllegalArgumentException.class, () -> new KtoFestivalItem(
			place,
			LocalDate.of(2026, 10, 18),
			LocalDate.of(2026, 10, 16),
			null,
			null));
	}

	@Test
	void requiresAFestivalTitleAndContentType() {
		assertThrows(IllegalArgumentException.class, () -> new KtoFestivalItem(
			place("700001", null),
			LocalDate.of(2026, 10, 16),
			LocalDate.of(2026, 10, 18),
			null,
			null));
		assertThrows(IllegalArgumentException.class, () -> new KtoFestivalItem(
			place("700001", "Festival", "12"),
			LocalDate.of(2026, 10, 16),
			LocalDate.of(2026, 10, 18),
			null,
			null));
	}

	private KtoPlaceItem place(String contentId, String title) {
		return place(contentId, title, "15");
	}

	private KtoPlaceItem place(String contentId, String title, String contentTypeId) {
		return new KtoPlaceItem(
			contentId, contentTypeId, title, null, null, null, null,
			null, null, null, null, null, null, null, null, null,
			null, null, null, null, null, null, null, null, null, null,
			"a".repeat(64));
	}
}
