package koready_backend.home.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.common.controller.TraceIdFilter;
import koready_backend.home.application.HomeService;
import koready_backend.home.application.exception.HomeUserUnavailableException;
import koready_backend.home.application.port.HomeRepository.HomeLocation;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.MonthlyRecommendationService;
import koready_backend.recommendation.domain.FestivalOccurrenceStatus;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeControllerTest {

	private static final String USER_PUBLIC_ID = "usr_home_controller";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	HomeService service;

	@BeforeEach
	void serviceDefaults() {
		when(service.getHome(USER_PUBLIC_ID)).thenReturn(home());
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/home"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsHomeDataForTheAuthenticatedUser() throws Exception {
		mockMvc.perform(get("/api/v1/home")
				.with(user(USER_PUBLIC_ID).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(header().exists(TraceIdFilter.HEADER_NAME))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("HOME_OK"))
			.andExpect(jsonPath("$.data.currentLocation.locationId").value(10))
			.andExpect(jsonPath("$.data.preferredLanguage").value("EN"))
			.andExpect(jsonPath("$.data.monthlyRecommendation.year").value(2026))
			.andExpect(jsonPath("$.data.monthlyRecommendation.month").value(7))
			.andExpect(jsonPath("$.data.monthlyRecommendation.totalCount").value(12))
			.andExpect(jsonPath("$.data.monthlyRecommendation.items", hasSize(1)))
			.andExpect(jsonPath("$.data.monthlyRecommendation.items[0].festivalOccurrence.status")
				.value("ONGOING"));
	}

	@Test
	void rejectsAStaleAuthenticatedPrincipal() throws Exception {
		when(service.getHome("usr_missing"))
			.thenThrow(new HomeUserUnavailableException());

		mockMvc.perform(get("/api/v1/home")
				.with(user("usr_missing").roles("USER")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private HomeService.Home home() {
		return new HomeService.Home(
			new HomeLocation(10L, "Campus", ServiceRegionCode.SEOUL),
			PlaceLanguage.EN,
			new HomeService.MonthlyRecommendationPreview(
				2026,
				7,
				"July picks you should not miss!",
				12L,
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
					false))));
	}
}
