package koready_backend.recommendation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
	private final MonthlyRecommendationService service =
		new MonthlyRecommendationService(repository, CLOCK);

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
			TravelStyle.LOCAL_FESTIVAL,
			"Festival overview",
			new BigDecimal(score),
			statusRank);
	}
}
