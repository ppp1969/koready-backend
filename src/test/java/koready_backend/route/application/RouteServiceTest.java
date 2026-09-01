package koready_backend.route.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.horitip.application.HoriTipService;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.route.application.port.RouteRepository;
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
		assertEquals("SUBWAY", view.route().summary().recommendedTransportText());
		assertEquals("Take Line 3.", view.route().segments().getFirst().instruction());

		ArgumentCaptor<RoutePlan> saved = ArgumentCaptor.forClass(RoutePlan.class);
		verify(repository).save(eq(1L), saved.capture());
		assertEquals("EN", saved.getValue().language());
		assertEquals(NOW.plusSeconds(30 * 60), saved.getValue().expiresAt());
	}
}
