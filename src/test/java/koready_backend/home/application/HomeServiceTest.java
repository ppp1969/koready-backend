package koready_backend.home.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.home.application.exception.HomeUserUnavailableException;
import koready_backend.home.application.port.HomeRepository;
import koready_backend.home.application.port.HomeRepository.HomeLocation;
import koready_backend.home.application.port.HomeRepository.HomeUser;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.MonthlyRecommendationService;
import koready_backend.recommendation.domain.DateFilterType;
import koready_backend.recommendation.domain.FestivalOccurrenceStatus;
import koready_backend.recommendation.domain.RecommendationSort;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

	private static final String USER_PUBLIC_ID = "usr_home_service";
	private static final Instant NOW = Instant.parse("2026-07-18T15:30:00Z");

	@Mock
	HomeRepository repository;

	@Mock
	MonthlyRecommendationService monthlyRecommendationService;

	private HomeService service;

	@BeforeEach
	void setUp() {
		service = new HomeService(
			repository,
			monthlyRecommendationService,
			Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
	}

	@Test
	void returnsCurrentLocationLanguageAndCurrentMonthPreview() {
		HomeLocation location = new HomeLocation(10L, "Campus", ServiceRegionCode.SEOUL);
		when(repository.findByPublicId(USER_PUBLIC_ID))
			.thenReturn(Optional.of(new HomeUser(
				7L, USER_PUBLIC_ID, PlaceLanguage.EN, location)));
		when(monthlyRecommendationService.getMonthlyRecommendations(
			eq(2026),
			eq(7),
			eq(ServiceRegionCode.SEOUL),
			eq(DateFilterType.ALL),
			eq(null),
			eq(null),
			eq(List.of()),
			eq(RecommendationSort.RECOMMENDED),
			eq(null),
			eq(5),
			eq(PlaceLanguage.EN)))
			.thenReturn(monthlyPage());

		HomeService.Home result = service.getHome(USER_PUBLIC_ID);

		assertEquals(location, result.currentLocation());
		assertEquals(PlaceLanguage.EN, result.preferredLanguage());
		assertEquals(2026, result.monthlyRecommendation().year());
		assertEquals(7, result.monthlyRecommendation().month());
		assertEquals("July picks you should not miss!", result.monthlyRecommendation().title());
		assertEquals(12L, result.monthlyRecommendation().totalCount());
		assertEquals(List.of(101L), result.monthlyRecommendation().items().stream()
			.map(MonthlyRecommendationService.PlaceCard::placeId)
			.toList());
	}

	@Test
	void returnsAnEmptyPreviewWithoutAUsableDefaultLocation() {
		when(repository.findByPublicId(USER_PUBLIC_ID))
			.thenReturn(Optional.of(new HomeUser(
				7L, USER_PUBLIC_ID, PlaceLanguage.KO, null)));

		HomeService.Home result = service.getHome(USER_PUBLIC_ID);

		assertNull(result.currentLocation());
		assertEquals("7월엔 이건 해야지!", result.monthlyRecommendation().title());
		assertEquals(0L, result.monthlyRecommendation().totalCount());
		assertTrue(result.monthlyRecommendation().items().isEmpty());
		verify(monthlyRecommendationService, never()).getMonthlyRecommendations(
			any(Integer.class),
			any(Integer.class),
			any(),
			any(),
			any(),
			any(),
			any(),
			any(),
			any(),
			any(Integer.class),
			any());
	}

	@Test
	void rejectsAnAuthenticatedPrincipalWithoutAnActiveUser() {
		when(repository.findByPublicId(USER_PUBLIC_ID)).thenReturn(Optional.empty());

		assertThrows(HomeUserUnavailableException.class,
			() -> service.getHome(USER_PUBLIC_ID));
	}

	private MonthlyRecommendationService.MonthlyRecommendationPage monthlyPage() {
		return new MonthlyRecommendationService.MonthlyRecommendationPage(
			2026,
			7,
			new MonthlyRecommendationService.AppliedFilters(
				2026,
				7,
				ServiceRegionCode.SEOUL,
				DateFilterType.ALL,
				null,
				null,
				List.of(),
				RecommendationSort.RECOMMENDED),
			List.of(new MonthlyRecommendationService.PlaceCard(
				101L,
				"Summer festival",
				ServiceRegionCode.SEOUL,
				"Seoul",
				"Jung-gu, Seoul",
				null,
				new MonthlyRecommendationService.FestivalOccurrenceSummary(
					501L,
					2026,
					LocalDate.of(2026, 7, 10),
					LocalDate.of(2026, 7, 20),
					FestivalOccurrenceStatus.ONGOING,
					"Jul 10, 2026 - Jul 20, 2026"),
				TravelStyle.LOCAL_FESTIVAL,
				List.of(),
				"A local summer festival.",
				false)),
			null,
			false,
			12L);
	}
}
