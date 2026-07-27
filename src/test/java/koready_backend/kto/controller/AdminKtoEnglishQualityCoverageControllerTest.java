package koready_backend.kto.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.kto.application.KtoEnglishQualityCoverageService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminKtoEnglishQualityCoverageControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	KtoEnglishQualityCoverageService service;

	@Test
	void protectsAndReturnsTheCoverage() throws Exception {
		when(service.coverage()).thenReturn(
			new KtoEnglishQualityCoverageService.Coverage(
				Instant.parse("2026-07-27T00:00:00Z"),
				100,
				40,
				60,
				new BigDecimal("40.00"),
				30,
				5,
				2,
				3));

		mockMvc.perform(get("/api/v1/admin/kto/english-quality-coverage"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/admin/kto/english-quality-coverage")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code")
				.value("KTO_ENGLISH_QUALITY_COVERAGE_OK"))
			.andExpect(jsonPath("$.data.total").value(100))
			.andExpect(jsonPath("$.data.classified").value(40))
			.andExpect(jsonPath("$.data.pending").value(60))
			.andExpect(jsonPath("$.data.completionRate").value(40.00));
	}
}
