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

import koready_backend.kto.application.KtoPhotoAwardCurationService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminKtoPhotoAwardControllerTest {

	private static final Instant NOW = Instant.parse("2026-07-27T03:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	KtoPhotoAwardCurationService service;

	@BeforeEach
	void defaults() {
		when(service.list(isNull(), isNull(), anyLong(), anyInt()))
			.thenReturn(new KtoPhotoAwardCurationService.PhotoAwardPage(
				List.of(award(null)), "10", true));
		when(service.approveMapping(anyString(), any(), anyString()))
			.thenReturn(award(205L));
	}

	@Test
	void listsAwardCandidatesForAllAdminReadRoles() throws Exception {
		mockMvc.perform(get("/api/v1/admin/kto/photo-awards"))
			.andExpect(status().isUnauthorized());

		for (String role : List.of("ADMIN", "OPERATOR", "AUDITOR")) {
			mockMvc.perform(get("/api/v1/admin/kto/photo-awards")
					.with(user(role.toLowerCase()).roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("KTO_PHOTO_AWARD_LIST_OK"))
				.andExpect(jsonPath("$.data.items[0].contentId")
					.value("award-001"))
				.andExpect(jsonPath("$.data.items[0].titleKo")
					.value("궁궐의 아침"))
				.andExpect(jsonPath("$.data.items[0].mappedPlaceId")
					.doesNotExist())
				.andExpect(jsonPath("$.data.hasMore").value(true));
		}
	}

	@Test
	void allowsOnlyAdminAndOperatorToApproveAndRemoveMappings()
		throws Exception {
		String approveBody = """
			{
			  "placeId": 205,
			  "displayOrder": 1,
			  "reason": "운영진이 원본 촬영 장소를 확인함"
			}
			""";

		mockMvc.perform(put("/api/v1/admin/kto/photo-awards/award-001/mapping")
				.with(user("auditor").roles("AUDITOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(approveBody))
			.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/admin/kto/photo-awards/award-001/mapping")
				.with(user("operator-1").roles("OPERATOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(approveBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code")
				.value("KTO_PHOTO_AWARD_MAPPING_APPROVED"))
			.andExpect(jsonPath("$.data.mappedPlaceId").value(205))
			.andExpect(jsonPath("$.data.displayOrder").value(1));

		mockMvc.perform(delete("/api/v1/admin/kto/photo-awards/award-001/mapping")
				.with(user("operator-1").roles("OPERATOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"잘못 연결된 장소를 해제함\"}"))
			.andExpect(status().isNoContent());

		verify(service).removeMapping(
			"award-001", "잘못 연결된 장소를 해제함", "operator-1");
	}

	@Test
	void rejectsInvalidApprovalInputAndCursor() throws Exception {
		mockMvc.perform(put("/api/v1/admin/kto/photo-awards/award-001/mapping")
				.with(user("operator").roles("OPERATOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "placeId": 0,
					  "displayOrder": 0,
					  "reason": ""
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(get("/api/v1/admin/kto/photo-awards")
				.queryParam("cursor", "not-a-number")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isBadRequest());
	}

	private KtoPhotoAwardCurationService.PhotoAwardView award(
		Long mappedPlaceId
	) {
		return new KtoPhotoAwardCurationService.PhotoAwardView(
			"award-001",
			"궁궐의 아침",
			"서울 경복궁",
			"경복궁,궁궐,아침",
			"Morning at the Palace",
			"Gyeongbokgung Palace, Seoul",
			"palace,morning,seoul",
			"https://example.invalid/photo-award/original-001.jpg",
			"https://example.invalid/photo-award/thumb-001.jpg",
			"Type1",
			mappedPlaceId,
			mappedPlaceId == null ? null : "경복궁",
			mappedPlaceId == null ? null : 1,
			mappedPlaceId == null ? null : "operator-1",
			mappedPlaceId == null ? null : "운영진 확인",
			mappedPlaceId == null ? null : NOW,
			NOW);
	}
}
