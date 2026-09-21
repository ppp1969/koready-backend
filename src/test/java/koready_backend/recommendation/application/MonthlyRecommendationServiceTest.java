package koready_backend.recommendation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import koready_backend.place.application.port.SavedPlaceStatusPort;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.exception.InvalidDateRangeException;
import koready_backend.recommendation.application.exception.InvalidRecommendationCursorException;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationFilter;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationPageQuery;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationRow;
import koready_backend.recommendation.domain.DateFilterType;
import koready_backend.recommendation.domain.RecommendationSort;

class MonthlyRecommendationServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 7, 18);
	private static final Clock CLOCK = Clock.fixed(
		Instant.parse("2026-07-18T03:00:00Z"), ZoneId.of("Asia/Seoul"));

	private final MonthlyRecommendationRepository repository =
		mock(MonthlyRecommendationRepository.class);
	private final SavedPlaceStatusPort savedPlaceStatusPort =
		mock(SavedPlaceStatusPort.class);
	private final MonthlyRecommendationService service =
		new MonthlyRecommendationService(repository, savedPlaceStatusPort, CLOCK);

	@Test
	void deadlineIncludesEvergreenAndContinuesAfterNullEndDate() {
		MonthlyRecommendationRow evergreen = new MonthlyRecommendationRow(
			-101L, 101L, 0, null, null, "Nature place",
			ServiceRegionCode.SEOUL, "Seoul", "Seoul", null, "Always open",
			TravelStyle.NATURE, "Overview", 3L, new BigDecimal("85"), 1, 100);
		when(repository.findPage(any())).thenReturn(List.of(evergreen, evergreen), List.of());
		var first = service.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.ALL, null, null, List.of(),
			RecommendationSort.DEADLINE, null, 1, PlaceLanguage.KO);
		service.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.ALL, null, null, List.of(),
			RecommendationSort.DEADLINE, first.nextCursor(), 1, PlaceLanguage.KO);
		var captor = ArgumentCaptor.forClass(MonthlyRecommendationPageQuery.class);
		verify(repository, org.mockito.Mockito.times(2)).findPage(captor.capture());
		assertTrue(captor.getAllValues().getFirst().filter().includeEvergreen());
		assertNull(captor.getValue().cursor().endDate());
		assertEquals(-101L, captor.getValue().cursor().occurrenceId());
	}

	@Test
	void rejectsOldOrMalformedDeadlineCursorAndCursorFromPreviousDay() {
		when(repository.findPage(any())).thenReturn(List.of(
			row(51, TODAY.minusDays(3), TODAY.minusDays(2), 0, "90"),
			row(52, TODAY, TODAY.plusDays(2), 0, "80")));
		var first = service.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.ALL, null, null, List.of(),
			RecommendationSort.DEADLINE, null, 1, PlaceLanguage.KO);
		var nextDayService = new MonthlyRecommendationService(repository,
			Clock.offset(CLOCK, java.time.Duration.ofDays(1)));
		assertThrows(InvalidRecommendationCursorException.class, () -> nextDayService.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.ALL, null, null, List.of(),
			RecommendationSort.DEADLINE, first.nextCursor(), 1, PlaceLanguage.KO));
		String payload = new String(java.util.Base64.getUrlDecoder().decode(first.nextCursor()),
			java.nio.charset.StandardCharsets.UTF_8);
		for (String[] mutation : List.of(new String[]{"0", "2"}, new String[]{"6", ""},
			new String[]{"8", "-1"}, new String[]{"8", "1001"}, new String[]{"3", "1"})) {
			String[] parts = payload.split("\t", -1);
			parts[Integer.parseInt(mutation[0])] = mutation[1];
			String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				String.join("\t", parts).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			assertThrows(InvalidRecommendationCursorException.class, () -> service.getMonthlyRecommendations(
				2026, 7, null, DateFilterType.ALL, null, null, List.of(),
				RecommendationSort.DEADLINE, token, 1, PlaceLanguage.KO));
		}
	}

	@Test
	void returnsEvergreenCardWithoutFestivalOccurrence() {
		MonthlyRecommendationRow evergreen = new MonthlyRecommendationRow(
			-101L, 101L, 0, null, null, "Nature place",
			ServiceRegionCode.SEOUL, "Seoul", "Jongno-gu, Seoul", null,
			"Always open",
			TravelStyle.NATURE, "Nature overview", 3L,
			new BigDecimal("85.00"), 1, 0);
		when(repository.findPage(any())).thenReturn(List.of(evergreen));
		when(repository.count(any())).thenReturn(1L);

		var page = service.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.ALL, null, null, List.of(),
			RecommendationSort.RECOMMENDED, null, 20, PlaceLanguage.KO);

		assertEquals(101L, page.items().getFirst().placeId());
		assertNull(page.items().getFirst().festivalOccurrence());
		assertEquals("Always open", page.items().getFirst().operatingHours());
	}

	@Test
	void reflectsTheAuthenticatedUsersSavedState() {
		MonthlyRecommendationRow saved = row(
			31, TODAY.minusDays(1), TODAY.plusDays(1), 0, "90.00");
		MonthlyRecommendationRow unsaved = row(
			32, TODAY.plusDays(2), TODAY.plusDays(3), 1, "80.00");
		when(repository.findPage(any())).thenReturn(List.of(saved, unsaved));
		when(repository.count(any())).thenReturn(2L);
		when(savedPlaceStatusPort.findSavedPlaceIds(
			"usr_monthly", List.of(saved.placeId(), unsaved.placeId())))
			.thenReturn(Set.of(saved.placeId()));

		var page = service.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.ALL, null, null, List.of(),
			RecommendationSort.RECOMMENDED, null, 20, PlaceLanguage.KO,
			"usr_monthly");

		assertEquals(List.of(true, false),
			page.items().stream()
				.map(MonthlyRecommendationService.PlaceCard::saved)
				.toList());
	}

	@Test
	void keepsEndedOccurrenceAndCalculatesEveryStatusFromSeoulToday() {
		when(repository.findPage(any())).thenReturn(List.of(
			row(31, TODAY.minusDays(5), TODAY.minusDays(2), 2, "99.00"),
			row(32, TODAY.minusDays(1), TODAY.plusDays(1), 0, "80.00"),
			row(33, TODAY.plusDays(2), TODAY.plusDays(4), 1, "70.00")));
		when(repository.count(any())).thenReturn(3L);

		MonthlyRecommendationService.MonthlyRecommendationPage page = service.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.ALL, null, null, List.of(),
			RecommendationSort.RECOMMENDED, null, 20, PlaceLanguage.KO);

		ArgumentCaptor<MonthlyRecommendationPageQuery> captor =
			ArgumentCaptor.forClass(MonthlyRecommendationPageQuery.class);
		verify(repository).findPage(captor.capture());
		MonthlyRecommendationFilter filter = captor.getValue().filter();
		assertEquals(LocalDate.of(2026, 7, 1), filter.startDate());
		assertEquals(LocalDate.of(2026, 7, 31), filter.endDate());
		assertEquals(TODAY, filter.today());
		assertTrue(filter.includeEvergreen());
		assertEquals(List.of("ENDED", "ONGOING", "UPCOMING"),
			page.items().stream().map(item -> item.festivalOccurrence().status().name()).toList());
		assertEquals(3L, page.totalCount());
		assertFalse(page.hasMore());
	}

	@Test
	void intersectsRelativeDateFilterWithSelectedMonth() {
		when(repository.findPage(any())).thenReturn(List.of());
		when(repository.count(any())).thenReturn(0L);

		service.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.THIS_WEEK, null, null, List.of(),
			RecommendationSort.RECOMMENDED, null, 20, PlaceLanguage.KO);

		ArgumentCaptor<MonthlyRecommendationPageQuery> captor =
			ArgumentCaptor.forClass(MonthlyRecommendationPageQuery.class);
		verify(repository).findPage(captor.capture());
		assertEquals(LocalDate.of(2026, 7, 13), captor.getValue().filter().startDate());
		assertEquals(LocalDate.of(2026, 7, 19), captor.getValue().filter().endDate());
		assertFalse(captor.getValue().filter().includeEvergreen());
	}

	@Test
	void returnsEmptyPageWithoutDatabaseWorkWhenDateWindowsDoNotIntersect() {
		MonthlyRecommendationService.MonthlyRecommendationPage page = service.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.NEXT_MONTH, null, null, List.of(),
			RecommendationSort.RECOMMENDED, null, 20, PlaceLanguage.KO);

		assertTrue(page.items().isEmpty());
		assertEquals(0L, page.totalCount());
		verify(repository, never()).findPage(any());
		verify(repository, never()).count(any());
	}

	@Test
	void rejectsIncompleteOrReversedCustomDateRange() {
		assertThrows(InvalidDateRangeException.class, () -> service.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.CUSTOM, TODAY, null, List.of(),
			RecommendationSort.RECOMMENDED, null, 20, PlaceLanguage.KO));
		assertThrows(InvalidDateRangeException.class, () -> service.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.CUSTOM, TODAY.plusDays(1), TODAY, List.of(),
			RecommendationSort.RECOMMENDED, null, 20, PlaceLanguage.KO));
	}

	@Test
	void createsOpaqueCursorAndRejectsItAfterFilterChange() {
		when(repository.findPage(any())).thenReturn(List.of(
			row(41, TODAY.minusDays(1), TODAY.plusDays(1), 0, "90.00"),
			row(42, TODAY.plusDays(2), TODAY.plusDays(3), 1, "80.00"),
			row(43, TODAY.minusDays(5), TODAY.minusDays(2), 2, "100.00")), List.of());
		when(repository.count(any())).thenReturn(3L);

		MonthlyRecommendationService.MonthlyRecommendationPage first =
			service.getMonthlyRecommendations(
				2026, 7, null, DateFilterType.ALL, null, null,
				List.of(TravelStyle.NATURE, TravelStyle.NATURE),
				RecommendationSort.RECOMMENDED, null, 2, PlaceLanguage.EN);
		service.getMonthlyRecommendations(
			2026, 7, null, DateFilterType.ALL, null, null,
			List.of(TravelStyle.NATURE), RecommendationSort.RECOMMENDED,
			first.nextCursor(), 2, PlaceLanguage.EN);

		assertTrue(first.hasMore());
		assertNotNull(first.nextCursor());
		assertFalse(first.nextCursor().contains("80.00"));
		ArgumentCaptor<MonthlyRecommendationPageQuery> captor =
			ArgumentCaptor.forClass(MonthlyRecommendationPageQuery.class);
		verify(repository, org.mockito.Mockito.times(2)).findPage(captor.capture());
		MonthlyRecommendationPageQuery second = captor.getAllValues().get(1);
		assertEquals(1, second.cursor().statusRank());
		assertEquals(0L, second.cursor().heartCount());
		assertEquals(100, second.cursor().curationPriority());
		assertEquals(new BigDecimal("80"), second.cursor().qualityScore());
		assertEquals(42L, second.cursor().occurrenceId());
		assertEquals(List.of(TravelStyle.NATURE), second.filter().travelStyles());

		assertThrows(InvalidRecommendationCursorException.class,
			() -> service.getMonthlyRecommendations(
				2026, 7, ServiceRegionCode.JEJU, DateFilterType.ALL, null, null,
				List.of(TravelStyle.NATURE), RecommendationSort.RECOMMENDED,
				first.nextCursor(), 2, PlaceLanguage.EN));
	}

	private static MonthlyRecommendationRow row(
		long occurrenceId,
		LocalDate startDate,
		LocalDate endDate,
		int statusRank,
		String score
	) {
		return new MonthlyRecommendationRow(
			occurrenceId,
			occurrenceId + 100,
			startDate.getYear(),
			startDate,
			endDate,
			"Festival " + occurrenceId,
			ServiceRegionCode.SEOUL,
			"Seoul",
			"Jongno-gu, Seoul",
			null,
			"10:00-18:00",
			TravelStyle.LOCAL_FESTIVAL,
			"Festival overview",
			0L,
			new BigDecimal(score),
			statusRank, 100);
	}
}
