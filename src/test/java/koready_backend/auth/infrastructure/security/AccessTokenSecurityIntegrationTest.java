package koready_backend.auth.infrastructure.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.auth.application.port.AccessTokenPort;
import koready_backend.auth.domain.UserRole;
import koready_backend.dataquality.application.DataQualityAdminService;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.UserLanguageService;
import koready_backend.user.domain.NextStep;

@SpringBootTest(properties = {
	"koready.security.jwt.secret=test-only-secret-with-at-least-thirty-two-bytes",
	"koready.security.jwt.issuer=https://api.koready.cloud",
	"koready.security.jwt.audience=koready-api"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessTokenSecurityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccessTokenPort accessTokens;

	@MockitoBean
	private UserLanguageService userLanguageService;

	@MockitoBean
	private DataQualityAdminService dataQualityAdminService;

	@Test
	void aKoReadyAccessTokenAuthenticatesExistingProtectedApis() throws Exception {
		Instant issuedAt = Instant.now();
		String accessToken = accessTokens.issue(
			"usr_access_integration",
			UserRole.USER,
			issuedAt).value();
		when(userLanguageService.update(
			"usr_access_integration", PlaceLanguage.EN))
			.thenReturn(new UserLanguageService.LanguageResult(
				PlaceLanguage.EN,
				NextStep.ONBOARDING,
				issuedAt));

		mockMvc.perform(patch("/api/v1/users/me/language")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"language\":\"EN\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.language").value("EN"));
	}

	@Test
	void theRoleClaimControlsAdminApiAccess() throws Exception {
		Instant issuedAt = Instant.now();
		when(dataQualityAdminService.summary()).thenReturn(
			new DataQualityAdminService.DataQualitySummary(
				issuedAt,
				new DataQualityAdminService.PlaceQualitySummary(0, 0, 0, 0, 0, 0, 0),
				new DataQualityAdminService.LocalizationQualitySummary(0, 0, 0),
				null));
		String userToken = accessTokens.issue(
			"usr_member",
			UserRole.USER,
			issuedAt).value();
		String adminToken = accessTokens.issue(
			"usr_admin",
			UserRole.ADMIN,
			issuedAt).value();

		mockMvc.perform(get("/api/v1/admin/data-quality/summary")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));
		mockMvc.perform(get("/api/v1/admin/data-quality/summary")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("DATA_QUALITY_SUMMARY_OK"));
	}
}
