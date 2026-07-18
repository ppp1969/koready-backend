package koready_backend.onboarding.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.common.controller.TraceIdFilter;
import koready_backend.onboarding.application.CandidateSetService;
import koready_backend.onboarding.application.CandidateSetService.AdminCandidateItem;
import koready_backend.onboarding.application.CandidateSetService.AdminCandidateSet;
import koready_backend.onboarding.application.CandidateSetService.CandidateSetPage;
import koready_backend.onboarding.application.CandidateSetService.CandidateSetSummary;
import koready_backend.onboarding.application.CandidateSetService.CurrentCandidateItem;
import koready_backend.onboarding.application.CandidateSetService.CurrentCandidateSet;
import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidateSetControllerTest {

	private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CandidateSetService service;

	@Test
	void currentCandidateSetRequiresLoginAndReturnsLocalizedTypedEnvelope() throws Exception {
		when(service.getCurrent(PlaceLanguage.EN)).thenReturn(current());

		mockMvc.perform(get("/api/v1/onboarding/place-candidate-sets/current"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.traceId").isNotEmpty());

		mockMvc.perform(get("/api/v1/onboarding/place-candidate-sets/current")
				.with(user("member").roles("USER"))
				.header("Accept-Language", "en-US"))
			.andExpect(status().isOk())
			.andExpect(header().exists(TraceIdFilter.HEADER_NAME))
			.andExpect(jsonPath("$.code").value("ONBOARDING_CANDIDATE_SET_OK"))
			.andExpect(jsonPath("$.data.candidateSetId").value("onb-current"))
			.andExpect(jsonPath("$.data.minSelection").value(1))
			.andExpect(jsonPath("$.data.maxSelection").value(3))
			.andExpect(jsonPath("$.data.items", hasSize(1)))
			.andExpect(jsonPath("$.data.items[0].title").value("English place"));
	}

	@Test
	void adminReadsAllowAuditorButWritesRequireOperatorOrAdmin() throws Exception {
		when(service.list(eq(null), eq(null), eq(20)))
			.thenReturn(new CandidateSetPage(List.of(summary()), null, false));

		mockMvc.perform(get("/api/v1/admin/onboarding/place-candidate-sets")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ADMIN_CANDIDATE_SET_LIST_OK"))
			.andExpect(jsonPath("$.data.items", hasSize(1)));

		mockMvc.perform(post("/api/v1/admin/onboarding/place-candidate-sets")
				.with(user("member").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Summer\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"))
			.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

	@Test
	void operatorCanCreateUpdatePublishAndArchiveCandidateSet() throws Exception {
		when(service.createDraft(any(), eq("42"), eq(true))).thenReturn(draft());
		when(service.updateDraft(eq("onb-draft"), any(), eq("42"), eq(true)))
			.thenReturn(draft());
		when(service.publish("onb-draft", "42", true)).thenReturn(published());
		when(service.archive("onb-draft", "42", true)).thenReturn(archived());

		var operator = user("42").roles("OPERATOR");
		mockMvc.perform(post("/api/v1/admin/onboarding/place-candidate-sets")
				.with(operator)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Summer\",\"copyFromSetId\":null}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("ADMIN_CANDIDATE_SET_CREATED"))
			.andExpect(jsonPath("$.data.status").value("DRAFT"));

		mockMvc.perform(put("/api/v1/admin/onboarding/place-candidate-sets/onb-draft")
				.with(operator)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "Summer",
					  "items": [{
					    "placeId": 1,
					    "displayOrder": 1,
					    "curatorMessageKo": "Message",
					    "displayTags": ["tag"]
					  }]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ADMIN_CANDIDATE_SET_OK"));

		mockMvc.perform(post("/api/v1/admin/onboarding/place-candidate-sets/onb-draft/publish")
				.with(operator))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PUBLISHED"));

		mockMvc.perform(post("/api/v1/admin/onboarding/place-candidate-sets/onb-draft/archive")
				.with(operator))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("ARCHIVED"));

		verify(service).publish("onb-draft", "42", true);
	}

	@Test
	void rejectsInvalidAdminRequestBeforeCallingService() throws Exception {
		mockMvc.perform(post("/api/v1/admin/onboarding/place-candidate-sets")
				.with(user("42").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"   \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	private static CurrentCandidateSet current() {
		return new CurrentCandidateSet(
			"onb-current",
			1,
			CandidateSetStatus.PUBLISHED,
			NOW,
			1,
			3,
			List.of(new CurrentCandidateItem(
				1L,
				"English place",
				"https://example.com/place.jpg",
				ServiceRegionCode.SEOUL,
				"Seoul",
				TravelStyle.LOCAL_FESTIVAL,
				List.of("festival"),
				"English message",
				1)));
	}

	private static CandidateSetSummary summary() {
		return new CandidateSetSummary(
			"onb-draft", "Summer", 1, CandidateSetStatus.DRAFT,
			1, false, null, NOW, NOW);
	}

	private static AdminCandidateSet draft() {
		return set(CandidateSetStatus.DRAFT, true, null);
	}

	private static AdminCandidateSet published() {
		return set(CandidateSetStatus.PUBLISHED, false, NOW);
	}

	private static AdminCandidateSet archived() {
		return set(CandidateSetStatus.ARCHIVED, false, NOW);
	}

	private static AdminCandidateSet set(
		CandidateSetStatus status,
		boolean editable,
		Instant statusAt
	) {
		return new AdminCandidateSet(
			"onb-draft",
			"Summer",
			1,
			status,
			1,
			status == CandidateSetStatus.PUBLISHED,
			status == CandidateSetStatus.PUBLISHED ? statusAt : null,
			NOW,
			NOW,
			status == CandidateSetStatus.ARCHIVED ? statusAt : null,
			editable,
			status == CandidateSetStatus.PUBLISHED ? 42L : null,
			List.of(new AdminCandidateItem(
				1L,
				"Korean place",
				"English place",
				null,
				"https://example.com/place.jpg",
				1,
				"Message",
				null,
				List.of("tag"),
				null,
				true,
				List.of())));
	}
}
