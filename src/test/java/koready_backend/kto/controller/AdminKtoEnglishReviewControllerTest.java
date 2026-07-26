package koready_backend.kto.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.kto.application.KtoEnglishReviewService;
import koready_backend.kto.application.exception.KtoEnglishReviewCandidateRequiredException;
import koready_backend.kto.domain.KtoEnglishReviewStatus;
import koready_backend.kto.domain.KtoEnglishSourceQuality;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminKtoEnglishReviewControllerTest {

	private static final Instant NOW = Instant.parse("2026-07-27T01:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	KtoEnglishReviewService service;

	@BeforeEach
	void defaults() {
		when(service.list(any())).thenReturn(new KtoEnglishReviewService.ReviewPage(
			List.of(summary()), "next-cursor", true));
		when(service.get(31L)).thenReturn(detail());
		when(service.decide(anyLong(), any())).thenReturn(
			new KtoEnglishReviewService.ReviewDecisionView(
				31L,
				KtoEnglishReviewStatus.MANUAL_CONFIRMED,
				205L,
				1,
				"operator-1",
				"이미지와 좌표가 모두 일치함",
				NOW));
	}

	@Test
	void protectsTheQueueAndAllowsAdminReadRoles() throws Exception {
		mockMvc.perform(get("/api/v1/admin/kto/english-match-reviews"))
			.andExpect(status().isUnauthorized());

		for (String role : List.of("ADMIN", "OPERATOR", "AUDITOR")) {
			mockMvc.perform(get("/api/v1/admin/kto/english-match-reviews")
					.with(user(role.toLowerCase()).roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("KTO_ENGLISH_REVIEW_LIST_OK"))
				.andExpect(jsonPath("$.data.items[0].titleEn")
					.value("Gwangjang Market"))
				.andExpect(jsonPath("$.data.items[0].sourceQuality")
					.value("USABLE"))
				.andExpect(jsonPath("$.data.items[0].qualityWarnings").isEmpty())
				.andExpect(jsonPath("$.data.items[0].candidateCount").value(2))
				.andExpect(jsonPath("$.data.nextCursor").value("next-cursor"));
		}
	}

	@Test
	void acceptsAComputedQualityFilter() throws Exception {
		mockMvc.perform(get("/api/v1/admin/kto/english-match-reviews")
				.queryParam("quality", "NON_ENGLISH_SUSPECTED")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isOk());

		ArgumentCaptor<KtoEnglishReviewService.ReviewQuery> captor =
			ArgumentCaptor.forClass(KtoEnglishReviewService.ReviewQuery.class);
		verify(service).list(captor.capture());
		org.junit.jupiter.api.Assertions.assertEquals(
			KtoEnglishSourceQuality.NON_ENGLISH_SUSPECTED,
			captor.getValue().quality());
	}

	@Test
	void returnsSourceCandidatesAndAuditHistory() throws Exception {
		mockMvc.perform(get("/api/v1/admin/kto/english-match-reviews/31")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("KTO_ENGLISH_REVIEW_OK"))
			.andExpect(jsonPath("$.data.source.contentId").value("126508"))
			.andExpect(jsonPath("$.data.candidates[0].placeId").value(205))
			.andExpect(jsonPath("$.data.candidates[0].matchMethod")
				.value("EVIDENCE_CONFLICT"))
			.andExpect(jsonPath("$.data.audits").isEmpty());
	}

	@Test
	void onlyAdminAndOperatorCanDecide() throws Exception {
		String body = """
			{
			  "decision": "MANUAL_CONFIRMED",
			  "selectedPlaceId": 205,
			  "expectedVersion": 0,
			  "reason": "이미지와 좌표가 모두 일치함"
			}
			""";

		mockMvc.perform(put("/api/v1/admin/kto/english-match-reviews/31/decision")
				.with(user("auditor").roles("AUDITOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/admin/kto/english-match-reviews/31/decision")
				.with(user("operator-1").roles("OPERATOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("KTO_ENGLISH_REVIEW_DECIDED"))
			.andExpect(jsonPath("$.data.status").value("MANUAL_CONFIRMED"))
			.andExpect(jsonPath("$.data.decisionVersion").value(1))
			.andExpect(jsonPath("$.data.reviewedBy").value("operator-1"));
	}

	@Test
	void validatesDecisionShapeAndMapsMissingCandidate() throws Exception {
		mockMvc.perform(put("/api/v1/admin/kto/english-match-reviews/31/decision")
				.with(user("operator").roles("OPERATOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "decision": "REJECTED",
					  "expectedVersion": -1,
					  "reason": "후보 불일치"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		when(service.decide(anyLong(), any()))
			.thenThrow(new KtoEnglishReviewCandidateRequiredException());
		mockMvc.perform(put("/api/v1/admin/kto/english-match-reviews/31/decision")
				.with(user("operator").roles("OPERATOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "decision": "MANUAL_CONFIRMED",
					  "selectedPlaceId": 999,
					  "expectedVersion": 0,
					  "reason": "임의 후보 선택"
					}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code")
				.value("KTO_ENGLISH_REVIEW_CANDIDATE_REQUIRED"));
	}

	private static KtoEnglishReviewService.ReviewSummaryView summary() {
		return new KtoEnglishReviewService.ReviewSummaryView(
			31L,
			"126508",
			"Gwangjang Market",
			"88 Changgyeonggung-ro, Jongno-gu, Seoul",
			"https://example.com/market.jpg",
			true,
			KtoEnglishSourceQuality.USABLE,
			Set.of(),
			KtoEnglishReviewStatus.REVIEW_REQUIRED,
			2,
			0,
			null,
			NOW.minusSeconds(60),
			null);
	}

	private static KtoEnglishReviewService.ReviewDetailView detail() {
		return new KtoEnglishReviewService.ReviewDetailView(
			summary(),
			new KtoEnglishReviewService.SourceView(
				"126508",
				"126507",
				"12",
				"Gwangjang Market",
				"88 Changgyeonggung-ro",
				"Jongno-gu, Seoul",
				"https://example.com/market.jpg",
				"https://example.com/market-thumb.jpg",
				"126.998",
				"37.570",
				"20260727090000",
				"1",
				"a".repeat(64),
				11L),
			List.of(new KtoEnglishReviewService.CandidateView(
				205L,
				"광장시장",
				"서울특별시 종로구 창경궁로 88",
				"https://example.com/market-ko.jpg",
				"EVIDENCE_CONFLICT",
				0.8,
				2,
				1,
				2,
				true,
				false)),
			List.of());
	}
}
