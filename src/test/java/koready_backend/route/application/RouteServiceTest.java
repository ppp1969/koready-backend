package koready_backend.route.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.horitip.application.HoriTipService;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.route.application.port.RouteRepository;
import koready_backend.route.application.exception.RouteException;
import koready_backend.route.application.port.TransitRouteProvider;
import koready_backend.route.domain.RouteCandidate;
import koready_backend.route.domain.RouteMode;
import koready_backend.route.domain.RoutePlan;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-01T03:30:00Z");

	@Mock
	RouteRepository repository;

	@Mock
	TransitRouteProvider provider;

	@Mock
	HoriTipService horiTipService;

	@Test
	void passesUserLanguageToProviderAndPersistsNormalizedRoute() {
		when(repository.findContext("usr_route", 9L, 204L)).thenReturn(Optional.of(
			new RouteRepository.RouteContext(
				1L, "EN", "Dormitory", "Seoul", 37.5796, 126.9770,
				"Gyeongbokgung Palace", "161 Sajik-ro", 37.4979, 127.0276)));
		when(provider.findRoutes(any())).thenReturn(List.of(new RouteCandidate(
			2_400, 300, 400, 0, 1_500,
			List.of(new RouteCandidate.RouteLeg(
				RouteMode.SUBWAY, "Gyeongbokgung", "Gangnam", "Line 3",
				2_400, 12_000, 1_500, true)))));
		when(horiTipService.findActiveRouteTips(204L)).thenReturn(List.of());
		RouteService service = new RouteService(
			repository, provider, horiTipService, Clock.fixed(NOW, ZoneOffset.UTC));

		var view = service.create(
			"usr_route", new RouteService.CreateCommand(9L, 204L, null));

		ArgumentCaptor<TransitRouteProvider.RouteProviderRequest> request =
			ArgumentCaptor.forClass(TransitRouteProvider.RouteProviderRequest.class);
		verify(provider).findRoutes(request.capture());
		assertEquals(PlaceLanguage.EN, request.getValue().language());
		assertEquals(ZonedDateTime.parse("2026-09-01T10:00:00+09:00[Asia/Seoul]"),
			request.getValue().departureAt());
		assertEquals("SUBWAY", view.route().summary().recommendedTransportText());
		assertEquals("Take Line 3.", view.route().segments().getFirst().instruction());

		ArgumentCaptor<RoutePlan> saved = ArgumentCaptor.forClass(RoutePlan.class);
		verify(repository).save(eq(1L), saved.capture());
		assertEquals("EN", saved.getValue().language());
		assertEquals(NOW.plusSeconds(30 * 60), saved.getValue().expiresAt());
	}

	@ParameterizedTest
	@CsvSource({
		"2026-09-01T14:59:59Z,2026-09-01T10:00:00+09:00[Asia/Seoul]",
		"2026-09-01T15:00:00Z,2026-09-02T10:00:00+09:00[Asia/Seoul]"
	})
	void usesTenAmOnKoreanCalendarDate(String now, String expected) {
		stubContext();
		when(provider.findRoutes(any())).thenReturn(List.of());
		var service = new RouteService(repository, provider, horiTipService,
			Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
		var error = assertThrows(RouteException.class, () -> service.create(
			"usr_route", new RouteService.CreateCommand(9L, 204L, null)));
		assertEquals(RouteException.Reason.ROUTE_NOT_FOUND, error.reason());
		var request = ArgumentCaptor.forClass(TransitRouteProvider.RouteProviderRequest.class);
		verify(provider).findRoutes(request.capture());
		assertEquals(ZonedDateTime.parse(expected), request.getValue().departureAt());
	}

	@Test
	void preservesExplicitDepartureAndUnavailableSegmentsInReferenceRoute() {
		stubContext();
		when(provider.findRoutes(any())).thenReturn(List.of(new RouteCandidate(
			2400, 0, 0, 0, 1500, List.of(new RouteCandidate.RouteLeg(
				RouteMode.BUS, "Start", "End", "100", 2400, 12000, 1500, false)))));
		when(horiTipService.findActiveRouteTips(204L)).thenReturn(List.of());
		var service = new RouteService(repository, provider, horiTipService,
			Clock.fixed(NOW, ZoneOffset.UTC));
		var departure = ZonedDateTime.parse("2026-09-02T03:45:00Z");
		var view = service.create("usr_route", new RouteService.CreateCommand(9L, 204L, departure));
		var request = ArgumentCaptor.forClass(TransitRouteProvider.RouteProviderRequest.class);
		verify(provider).findRoutes(request.capture());
		assertEquals(departure.toInstant(), request.getValue().departureAt().toInstant());
		assertEquals("Asia/Seoul", request.getValue().departureAt().getZone().getId());
		assertFalse(view.route().segments().getFirst().serviceAvailable());
		verify(repository).save(eq(1L), any());
	}

	private void stubContext() {
		when(repository.findContext("usr_route", 9L, 204L)).thenReturn(Optional.of(
			new RouteRepository.RouteContext(1L, "EN", "Dormitory", "Seoul", 37.5, 127.0,
				"Destination", "Seoul", 37.6, 127.1)));
	}
}
