package koready_backend.kto.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.kto.application.KtoDetailCoverageService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminKtoDetailCoverageControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	KtoDetailCoverageService service;

	@BeforeEach
	void defaults() {
		when(service.summary()).thenReturn(new KtoDetailCoverageService.CoverageSummary(
			Instant.parse("2026-07-27T00:00:00Z"),
			new KtoDetailCoverageService.CatalogCoverage(
				7, 6, 1, 1, 85.71),
			new KtoDetailCoverageService.ImageCoverage(
				6, 2, 1, 1, 1, 1, 5, 83.33),
			new KtoDetailCoverageService.ImageCoverage(
				6, 1, 2, 1, 1, 1, 5, 83.33)));
	}

	@Test
	void requiresAnAdminReadRole() throws Exception {
		mockMvc.perform(get("/api/v1/admin/kto/detail-coverage"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/admin/kto/detail-coverage")
				.with(user("member").roles("USER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));

		for (String role : List.of("ADMIN", "OPERATOR", "AUDITOR")) {
			mockMvc.perform(get("/api/v1/admin/kto/detail-coverage")
					.with(user(role.toLowerCase()).roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code")
					.value("KTO_DETAIL_COVERAGE_OK"));
		}
	}

	@Test
	void returnsCatalogAndTwoImageCoverageViews() throws Exception {
		mockMvc.perform(get("/api/v1/admin/kto/detail-coverage")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.generatedAt")
				.value("2026-07-27T00:00:00Z"))
			.andExpect(jsonPath("$.data.catalog.totalPlaces").value(7))
			.andExpect(jsonPath("$.data.catalog.completedPlaces").value(6))
			.andExpect(jsonPath("$.data.catalog.pendingPlaces").value(1))
			.andExpect(jsonPath("$.data.catalog.dueForRefreshPlaces").value(1))
			.andExpect(jsonPath("$.data.catalog.completionRate").value(85.71))
			.andExpect(jsonPath("$.data.ktoDetailImages.zero").value(2))
			.andExpect(jsonPath("$.data.ktoDetailImages.fourOrMore").value(1))
			.andExpect(jsonPath("$.data.ktoDetailImages.lessThanFour").value(5))
			.andExpect(jsonPath("$.data.effectiveGalleryImages.one").value(2))
			.andExpect(jsonPath("$.data.effectiveGalleryImages.lessThanFourRate")
				.value(83.33));
	}
}
