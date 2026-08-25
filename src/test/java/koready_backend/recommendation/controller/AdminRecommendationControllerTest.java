package koready_backend.recommendation.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.recommendation.application.RecommendationExposureAdminService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRecommendationControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	RecommendationExposureAdminService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(delete(
			"/api/v1/admin/recommendations/users/usr-test/exposure-history"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(roles = "USER")
	void rejectsNonAdmin() throws Exception {
		mockMvc.perform(delete(
			"/api/v1/admin/recommendations/users/usr-test/exposure-history"))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void resetsRecommendationExposureHistory() throws Exception {
		when(service.reset("usr-test")).thenReturn(
			new RecommendationExposureAdminService.ResetView("usr-test", 3, 7));

		mockMvc.perform(delete(
			"/api/v1/admin/recommendations/users/usr-test/exposure-history"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_EXPOSURE_HISTORY_RESET"))
			.andExpect(jsonPath("$.data.userPublicId").value("usr-test"))
			.andExpect(jsonPath("$.data.deletedSuppressionStateCount").value(3))
			.andExpect(jsonPath("$.data.deletedCardServedEventCount").value(7));
	}
}
