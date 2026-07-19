package koready_backend.dataquality.controller;

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

import koready_backend.dataquality.application.DataQualityAdminService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDataQualityControllerTest {

	private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
	private static final Instant LAST_SYNC = Instant.parse("2026-07-19T08:30:00Z");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	DataQualityAdminService service;

	@BeforeEach
	void defaults() {
		when(service.summary()).thenReturn(new DataQualityAdminService.DataQualitySummary(
			NOW,
			new DataQualityAdminService.PlaceQualitySummary(4, 3, 1, 1, 1, 1, 2),
			new DataQualityAdminService.LocalizationQualitySummary(1, 1, 1),
			LAST_SYNC));
	}

	@Test
	void requiresAnAdminReadRole() throws Exception {
		mockMvc.perform(get("/api/v1/admin/data-quality/summary"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/admin/data-quality/summary")
				.with(user("member").roles("USER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));

		for (String role : List.of("ADMIN", "OPERATOR", "AUDITOR")) {
			mockMvc.perform(get("/api/v1/admin/data-quality/summary")
					.with(user(role.toLowerCase()).roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("DATA_QUALITY_SUMMARY_OK"));
		}
	}

	@Test
	void returnsOnlyAggregateQualityInformation() throws Exception {
		mockMvc.perform(get("/api/v1/admin/data-quality/summary")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.generatedAt").value("2026-07-19T12:00:00Z"))
			.andExpect(jsonPath("$.data.places.total").value(4))
			.andExpect(jsonPath("$.data.places.active").value(3))
			.andExpect(jsonPath("$.data.places.missingImage").value(1))
			.andExpect(jsonPath("$.data.places.missingEnglish").value(1))
			.andExpect(jsonPath("$.data.places.missingCoordinates").value(1))
			.andExpect(jsonPath("$.data.places.missingAddress").value(1))
			.andExpect(jsonPath("$.data.places.curationReady").value(2))
			.andExpect(jsonPath("$.data.localization.ktoEnglish").value(1))
			.andExpect(jsonPath("$.data.localization.aiTranslated").value(1))
			.andExpect(jsonPath("$.data.localization.manualEdited").value(1))
			.andExpect(jsonPath("$.data.lastSuccessfulSyncAt")
				.value("2026-07-19T08:30:00Z"))
			.andExpect(jsonPath("$.data.items").doesNotExist());
	}
}
