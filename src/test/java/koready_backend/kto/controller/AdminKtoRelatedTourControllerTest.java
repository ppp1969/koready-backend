package koready_backend.kto.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.kto.application.KtoRelatedTourCurationService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminKtoRelatedTourControllerTest {

	private static final Instant NOW =
		Instant.parse("2026-07-27T06:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	KtoRelatedTourCurationService service;

	@BeforeEach
	void defaults() {
		when(service.list(isNull(), isNull(), anyLong(), anyInt()))
			.thenReturn(
				new KtoRelatedTourCurationService.RelatedTourPage(
					List.of(record(null)), "10", true));
		when(service.confirmMapping(anyLong(), any(), anyString()))
			.thenReturn(record(205L));
	}

	@Test
	void listsRelatedTourCandidatesForAdminReadRoles() throws Exception {
		mockMvc.perform(get("/api/v1/admin/kto/related-tours"))
			.andExpect(status().isUnauthorized());

		for (String role : List.of("ADMIN", "OPERATOR", "AUDITOR")) {
			mockMvc.perform(get("/api/v1/admin/kto/related-tours")
					.with(user(role.toLowerCase()).roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code")
					.value("KTO_RELATED_TOUR_LIST_OK"))
				.andExpect(jsonPath("$.data.items[0].id").value(7))
				.andExpect(jsonPath("$.data.items[0].rank").value(1))
				.andExpect(jsonPath("$.data.items[0].matchStatus")
					.value("UNMATCHED"));
		}
	}

	@Test
	void allowsOperatorsToConfirmAndRemoveMappings() throws Exception {
		mockMvc.perform(put(
				"/api/v1/admin/kto/related-tours/7/mapping")
				.with(user("operator-1").roles("OPERATOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "sourcePlaceId": 101,
					  "relatedPlaceId": 205,
					  "reason": "두 장소의 이름과 행정구역을 확인했습니다."
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code")
				.value("KTO_RELATED_TOUR_MAPPING_CONFIRMED"))
			.andExpect(jsonPath("$.data.sourcePlaceId").value(101))
			.andExpect(jsonPath("$.data.relatedPlaceId").value(205));

		mockMvc.perform(delete(
				"/api/v1/admin/kto/related-tours/7/mapping")
				.with(user("operator-1").roles("OPERATOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reason":"연결된 장소가 잘못되어 해제합니다."}
					"""))
			.andExpect(status().isNoContent());

		verify(service).removeMapping(
			7L,
			"연결된 장소가 잘못되어 해제합니다.",
			"operator-1");
	}

	private KtoRelatedTourCurationService.RelatedTourView record(
		Long relatedPlaceId
	) {
		boolean mapped = relatedPlaceId != null;
		return new KtoRelatedTourCurationService.RelatedTourView(
			7L,
			"202606",
			"1".repeat(32),
			"경복궁",
			"서울특별시",
			"종로구",
			"2".repeat(32),
			"국립민속박물관",
			"서울특별시",
			"종로구",
			"역사관광",
			"문화시설",
			"박물관",
			1,
			mapped ? "MANUAL_CONFIRMED" : "UNMATCHED",
			mapped ? 101L : null,
			mapped ? "경복궁" : null,
			relatedPlaceId,
			mapped ? "국립민속박물관" : null,
			mapped ? "operator-1" : null,
			mapped ? "두 장소를 확인했습니다." : null,
			mapped ? NOW : null,
			NOW);
	}
}
