package koready_backend.location.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.location.application.LocationSearchService;
import koready_backend.location.application.exception.LocationProviderUnavailableException;
import koready_backend.location.domain.LocationSearchResultType;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocationSearchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LocationSearchService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/locations/search")
				.param("query", "성신여자대학교"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsNormalizedResults() throws Exception {
		when(service.search("성신여자대학교", 10, PlaceLanguage.KO)).thenReturn(
			new LocationSearchService.SearchResponse(List.of(
				new LocationSearchService.SearchItem(
					"locsrch_payload.signature",
					"KAKAO",
					PlaceLanguage.KO,
					LocationSearchResultType.PLACE,
					"123456789",
					"성신여자대학교",
					"서울특별시 성북구 보문로34다길 2",
					"서울특별시 성북구 돈암동 173-1",
					37.5928,
					127.0165,
					"서울특별시",
					"성북구",
					"돈암동",
					"02844",
					ServiceRegionCode.SEOUL))));

		mockMvc.perform(get("/api/v1/locations/search")
				.with(user("usr_emma").roles("USER"))
				.param("query", "성신여자대학교"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("LOCATION_SEARCH_OK"))
			.andExpect(jsonPath("$.data.items[0].provider").value("KAKAO"))
			.andExpect(jsonPath("$.data.items[0].language").value("KO"))
			.andExpect(jsonPath("$.data.items[0].resultType").value("PLACE"))
			.andExpect(jsonPath("$.data.items[0].providerPlaceId").value("123456789"))
			.andExpect(jsonPath("$.data.items[0].name").value("성신여자대학교"))
			.andExpect(jsonPath("$.data.items[0].postalCode").value("02844"))
			.andExpect(jsonPath("$.data.items[0].serviceRegionCode").value("SEOUL"));
	}

	@Test
	void returnsEnglishDisplayFieldsForEnglishSearch() throws Exception {
		when(service.search("Sungshin Women's University", 10, PlaceLanguage.EN))
			.thenReturn(new LocationSearchService.SearchResponse(List.of(
				new LocationSearchService.SearchItem(
					"locsrch_payload.signature",
					"GOOGLE_PLACES",
					PlaceLanguage.EN,
					LocationSearchResultType.PLACE,
					"google-place-1",
					"Sungshin Women's University",
					"2 Bomun-ro 34da-gil, Seongbuk-gu, Seoul",
					"2 Bomun-ro 34da-gil, Seongbuk-gu, Seoul",
					37.5928,
					127.0165,
					"Seoul",
					"Seongbuk-gu",
					null,
					"02844",
					ServiceRegionCode.SEOUL))));

		mockMvc.perform(get("/api/v1/locations/search")
				.with(user("usr_emma").roles("USER"))
				.param("query", "Sungshin Women's University")
				.param("language", "EN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].provider").value("GOOGLE_PLACES"))
			.andExpect(jsonPath("$.data.items[0].language").value("EN"))
			.andExpect(jsonPath("$.data.items[0].name")
				.value("Sungshin Women's University"))
			.andExpect(jsonPath("$.data.items[0].roadAddress")
				.value("2 Bomun-ro 34da-gil, Seongbuk-gu, Seoul"))
			.andExpect(jsonPath("$.data.items[0].address")
				.value("2 Bomun-ro 34da-gil, Seongbuk-gu, Seoul"));
	}

	@Test
	void rejectsBlankLongQueriesAndOutOfRangeLimits() throws Exception {
		for (String query : List.of("   ", "x".repeat(101))) {
			mockMvc.perform(get("/api/v1/locations/search")
					.with(user("usr_emma").roles("USER"))
					.param("query", query))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}

		for (String limit : List.of("0", "21")) {
			mockMvc.perform(get("/api/v1/locations/search")
					.with(user("usr_emma").roles("USER"))
					.param("query", "학교")
					.param("limit", limit))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
	}

	@Test
	void convertsProviderFailuresToServiceUnavailable() throws Exception {
		when(service.search("학교", 10, PlaceLanguage.KO))
			.thenThrow(new LocationProviderUnavailableException());

		mockMvc.perform(get("/api/v1/locations/search")
				.with(user("usr_emma").roles("USER"))
				.param("query", "학교"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("LOCATION_PROVIDER_UNAVAILABLE"));
	}
}
