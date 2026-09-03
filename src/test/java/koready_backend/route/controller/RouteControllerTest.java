package koready_backend.route.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.route.application.RouteService;
import koready_backend.route.application.exception.RouteException;
import koready_backend.route.domain.DayTripStatus;
import koready_backend.route.domain.RouteMode;
import koready_backend.route.domain.RoutePlan;
import koready_backend.route.domain.RoutePolicy;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RouteControllerTest {

	private static final String ROUTE_ID = "route_0123456789abcdef0123456789abcdef";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	RouteService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/routes")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"originLocationId\":1,\"destinationPlaceId\":2}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createsRealRouteContract() throws Exception {
		when(service.create(eq("usr_route"), any())).thenReturn(view());

		mockMvc.perform(post("/api/v1/routes")
				.with(user("usr_route").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"originLocationId\":1,\"destinationPlaceId\":2}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("ROUTE_CREATED"))
			.andExpect(jsonPath("$.data.routeId").value(ROUTE_ID))
			.andExpect(jsonPath("$.data.provider").value("TMAP_TRANSIT"))
			.andExpect(jsonPath("$.data.segments[0].mode").value("BUS"))
			.andExpect(jsonPath("$.data.detailAvailable").value(true));
	}

	@Test
	void exposesProviderFailureAs503InsteadOfEmptySuccess() throws Exception {
		when(service.create(eq("usr_route"), any())).thenThrow(new RouteException(
			RouteException.Reason.ROUTE_PROVIDER_UNAVAILABLE));

		mockMvc.perform(post("/api/v1/routes")
				.with(user("usr_route").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"originLocationId\":1,\"destinationPlaceId\":2}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("ROUTE_PROVIDER_UNAVAILABLE"));
	}

	@Test
	void returns410ForExpiredOrForeignRoute() throws Exception {
		when(service.get("usr_route", ROUTE_ID)).thenThrow(new RouteException(
			RouteException.Reason.ROUTE_EXPIRED));

		mockMvc.perform(get("/api/v1/routes/" + ROUTE_ID)
				.with(user("usr_route").roles("USER")))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("ROUTE_EXPIRED"));
	}

	private static RouteService.RouteView view() {
		return view("KO", true);
	}

	@ParameterizedTest
	@CsvSource({"KO,참고용 이동 경로입니다", "EN,This is a reference route"})
	void exposesUnavailableSegmentWarningsInCreateAndGet(String language, String message) throws Exception {
		when(service.create(eq("usr_route"), any())).thenReturn(view(language, false));
		when(service.get("usr_route", ROUTE_ID)).thenReturn(view(language, false));
		mockMvc.perform(post("/api/v1/routes")
				.with(user("usr_route").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"originLocationId\":1,\"destinationPlaceId\":2}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.segments[0].serviceAvailable").value(false))
			.andExpect(jsonPath("$.data.warnings[0].code").value("REFERENCE_ROUTE_SERVICE_UNAVAILABLE"))
			.andExpect(jsonPath("$.data.warnings[0].segmentOrder").value(1))
			.andExpect(jsonPath("$.data.warnings[0].message", org.hamcrest.Matchers.containsString(message)));
		mockMvc.perform(get("/api/v1/routes/" + ROUTE_ID).with(user("usr_route").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.segments[0].serviceAvailable").value(false))
			.andExpect(jsonPath("$.data.warnings[0].code").value("REFERENCE_ROUTE_SERVICE_UNAVAILABLE"));
	}

	private static RouteService.RouteView view(String language, boolean available) {
		var segment = new RoutePlan.RouteSegment(
			1, "출발", "도착", RouteMode.BUS, "100", 30, 10_000,
			1_500, available, "100을(를) 이용하세요.");
		var route = new RoutePlan(
			ROUTE_ID, 2L, language,
			new RoutePlan.RoutePoint("학교", "서울"),
			new RoutePlan.RoutePoint("축제", "강원"),
			Instant.parse("2026-08-31T00:00:00Z"),
			Instant.parse("2026-08-31T00:30:00Z"),
			3_600,
			new RoutePlan.RouteSummary(
				"버스", 60, "약 1시간 0분", 0, 0, 0,
				RoutePolicy.Difficulty.EASY, DayTripStatus.DAY_TRIP_AVAILABLE,
				new RoutePlan.RouteFare(1_500, 3_000, "AVAILABLE_SEGMENTS_ONLY"),
				List.of(RouteMode.BUS)),
			List.of(segment));
		return new RouteService.RouteView(
			route, List.of(), List.of(new RouteService.SegmentView(segment, List.of())));
	}
}
