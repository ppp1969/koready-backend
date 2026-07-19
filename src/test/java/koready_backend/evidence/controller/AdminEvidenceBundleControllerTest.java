package koready_backend.evidence.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import koready_backend.evidence.application.EvidenceBundleService;
import koready_backend.evidence.application.exception.EvidenceBundleNotCompletedException;
import koready_backend.evidence.domain.EvidenceBundleStatus;
import koready_backend.externalapi.domain.ExternalApiProvider;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminEvidenceBundleControllerTest {

	private static final Instant NOW = Instant.parse("2026-07-19T09:00:00Z");

	@Autowired MockMvc mockMvc;
	@MockitoBean EvidenceBundleService service;

	@BeforeEach
	void defaults() {
		when(service.create(any(), anyString())).thenReturn(bundle());
		when(service.list(any(), anyInt())).thenReturn(
			new EvidenceBundleService.BundlePage(List.of(bundle()), null, false));
		when(service.get("evidence_01J2ABCDEF")).thenReturn(bundle());
		when(service.createDownloadUrl(anyString(), anyString())).thenReturn(
			new EvidenceBundleService.DownloadView(
				"https://private.example/bundle?signature=temporary", NOW.plusSeconds(300),
				"koready-openapi-evidence-202607.zip", "a".repeat(64)));
	}

	@Test
	void createsBundleWith202AndAllowsAllAdminReadRoles() throws Exception {
		String request = """
			{
			  "name": "2026 공모전 OpenAPI 사용 증빙",
			  "from": "2026-07-01T00:00:00Z",
			  "to": "2026-07-19T09:00:00Z",
			  "providers": ["KTO"],
			  "operations": ["searchFestival2"],
			  "includeRawSnapshots": true,
			  "rawSampleLimitPerOperation": 3
			}
			""";

		mockMvc.perform(post("/api/v1/admin/evidence-bundles")
				.with(user("operator").roles("OPERATOR"))
				.contentType(MediaType.APPLICATION_JSON).content(request))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.code").value("EVIDENCE_BUNDLE_ACCEPTED"))
			.andExpect(jsonPath("$.data.bundleId").value("evidence_01J2ABCDEF"))
			.andExpect(jsonPath("$.data.status").value("QUEUED"));

		for (String role : List.of("ADMIN", "OPERATOR", "AUDITOR")) {
			mockMvc.perform(get("/api/v1/admin/evidence-bundles")
					.with(user(role.toLowerCase()).roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("EVIDENCE_BUNDLE_LIST_OK"));
		}
	}

	@Test
	void requiresAuthenticationAndValidatesRequest() throws Exception {
		mockMvc.perform(get("/api/v1/admin/evidence-bundles"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/admin/evidence-bundles")
				.with(user("admin").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void returnsDetailAndDownloadContract() throws Exception {
		mockMvc.perform(get("/api/v1/admin/evidence-bundles/evidence_01J2ABCDEF")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.providers[0]").value("KTO"));
		mockMvc.perform(post("/api/v1/admin/evidence-bundles/evidence_01J2ABCDEF/download-url")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("EVIDENCE_BUNDLE_DOWNLOAD_URL_ISSUED"))
			.andExpect(jsonPath("$.data.sha256").value("a".repeat(64)));
	}

	@Test
	void mapsIncompleteBundleToConflict() throws Exception {
		when(service.createDownloadUrl(anyString(), anyString()))
			.thenThrow(new EvidenceBundleNotCompletedException());
		mockMvc.perform(post("/api/v1/admin/evidence-bundles/evidence_01J2ABCDEF/download-url")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EVIDENCE_BUNDLE_NOT_COMPLETED"));
	}

	private static EvidenceBundleService.BundleView bundle() {
		return new EvidenceBundleService.BundleView(
			"evidence_01J2ABCDEF", "2026 공모전 OpenAPI 사용 증빙",
			EvidenceBundleStatus.QUEUED,
			new EvidenceBundleService.Period(NOW.minusSeconds(3600), NOW),
			List.of(ExternalApiProvider.KTO), List.of("searchFestival2"), true,
			null, null, null, null, null, NOW, null, null, null, List.of(), List.of());
	}
}
