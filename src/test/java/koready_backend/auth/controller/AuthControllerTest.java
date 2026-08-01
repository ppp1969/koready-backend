package koready_backend.auth.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import koready_backend.auth.application.GoogleAuthService;
import koready_backend.auth.application.GoogleAuthService.AuthResult;
import koready_backend.auth.application.GoogleAuthService.AuthUserSummary;
import koready_backend.auth.application.exception.InvalidGoogleIdTokenException;
import koready_backend.auth.application.exception.InvalidRefreshTokenException;
import koready_backend.auth.application.exception.AuthUnavailableException;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.domain.NextStep;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GoogleAuthService service;

	@Test
	void googleLoginIsAnonymousAndReturnsTheKoReadyTokenContract() throws Exception {
		when(service.login("google-id-token", "device-a")).thenReturn(result());

		mockMvc.perform(post("/api/v1/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "idToken": "google-id-token",
					  "deviceId": "device-a"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GOOGLE_LOGIN_OK"))
			.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.data.accessToken").value("access-token"))
			.andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
			.andExpect(jsonPath("$.data.user.userId").value(41))
			.andExpect(jsonPath("$.data.user.publicId").value("usr_a1"))
			.andExpect(jsonPath("$.data.nextStep").value("TERMS"));
	}

	@Test
	void refreshIsAnonymousButRejectsAnInvalidOrReusedToken() throws Exception {
		when(service.refresh("refresh-token", "device-a")).thenReturn(result());
		when(service.refresh("reused-token", "device-a"))
			.thenThrow(new InvalidRefreshTokenException());

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"refreshToken":"refresh-token","deviceId":"device-a"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("AUTH_TOKEN_REFRESHED"));

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"refreshToken":"reused-token","deviceId":"device-a"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
	}

	@Test
	void invalidGoogleTokenAndMalformedRequestsDoNotAuthenticate() throws Exception {
		when(service.login("forged-token", "device-a"))
			.thenThrow(new InvalidGoogleIdTokenException());

		mockMvc.perform(post("/api/v1/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"idToken":"forged-token","deviceId":"device-a"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("GOOGLE_ID_TOKEN_INVALID"));

		mockMvc.perform(post("/api/v1/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"idToken":"","deviceId":""}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void missingServerAuthenticationConfigurationReturnsServiceUnavailable()
		throws Exception {
		when(service.login("google-id-token", "device-a"))
			.thenThrow(new AuthUnavailableException());

		mockMvc.perform(post("/api/v1/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"idToken":"google-id-token","deviceId":"device-a"}
					"""))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));
	}

	@Test
	void logoutRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"refreshToken":"refresh-token","deviceId":"device-a"}
					"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = "local-user", roles = "USER")
	void authenticatedLogoutRevokesTheCurrentDeviceSession() throws Exception {
		doThrow(new InvalidRefreshTokenException())
			.when(service)
			.logout("local-user", "wrong-refresh", "device-a");

		mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"refreshToken":"wrong-refresh","deviceId":"device-a"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
	}

	@Test
	@WithMockUser(username = "usr_a1", roles = "USER")
	void logoutReturnsNoContentAfterRevokingTheSession() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"refreshToken":"refresh-token","deviceId":"device-a"}
					"""))
			.andExpect(status().isNoContent());

		verify(service).logout("usr_a1", "refresh-token", "device-a");
	}

	private static AuthResult result() {
		return new AuthResult(
			"access-token",
			"refresh-token",
			Instant.parse("2026-08-01T03:15:00Z"),
			Instant.parse("2026-08-31T03:00:00Z"),
			new AuthUserSummary(
				41L,
				"usr_a1",
				"verified@example.com",
				null,
				PlaceLanguage.KO),
			NextStep.TERMS);
	}
}
