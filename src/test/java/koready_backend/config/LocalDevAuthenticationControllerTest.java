package koready_backend.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.onboarding.application.CandidateSetService;
import koready_backend.onboarding.application.CandidateSetService.CandidateSetPage;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.UserLanguageService;
import koready_backend.user.domain.NextStep;

@SpringBootTest(properties = "koready.security.dev-principal.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class LocalDevAuthenticationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserLanguageService userLanguageService;

	@MockitoBean
	private CandidateSetService candidateSetService;

	@Test
	void localUserCanCallAProtectedUserApi() throws Exception {
		when(userLanguageService.update("local-user", PlaceLanguage.EN))
			.thenReturn(new UserLanguageService.LanguageResult(
				PlaceLanguage.EN,
				NextStep.ONBOARDING,
				Instant.parse("2026-07-19T02:00:00Z")));

		mockMvc.perform(patch("/api/v1/users/me/language")
				.header(HttpHeaders.AUTHORIZATION, "Bearer local-user")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"language\":\"EN\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.language").value("EN"));
	}

	@Test
	void fixedAdminRolesCanReadAdminApisButUserCannot() throws Exception {
		when(candidateSetService.list(eq(null), eq(null), eq(20)))
			.thenReturn(new CandidateSetPage(List.of(), null, false));

		for (String token : List.of("local-admin", "local-operator", "local-auditor")) {
			mockMvc.perform(get("/api/v1/admin/onboarding/place-candidate-sets")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk());
		}

		mockMvc.perform(get("/api/v1/admin/onboarding/place-candidate-sets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer local-user"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));
	}

	@Test
	void unknownLocalTokenRemainsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/users/me/onboarding")
				.header(HttpHeaders.AUTHORIZATION, "Bearer local-superuser"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
}
