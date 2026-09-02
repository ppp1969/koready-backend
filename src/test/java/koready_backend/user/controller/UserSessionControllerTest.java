package koready_backend.user.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.UserSessionService;
import koready_backend.user.application.exception.UserUnavailableException;
import koready_backend.user.domain.NextStep;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSessionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserSessionService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsTheCurrentServerStateForTheAuthenticatedUser() throws Exception {
		when(service.get("usr_me")).thenReturn(new UserSessionService.UserSession(
			7L, "usr_me", "me@example.com", null, PlaceLanguage.EN,
			UserSessionService.PublicSignupStatus.ACTIVE, NextStep.COMPLETED,
			12L, true, true, 3L, false));

		mockMvc.perform(get("/api/v1/users/me").with(user("usr_me").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("MY_USER_OK"))
			.andExpect(jsonPath("$.data.user.userId").value(7))
			.andExpect(jsonPath("$.data.user.publicId").value("usr_me"))
			.andExpect(jsonPath("$.data.user.email").value("me@example.com"))
			.andExpect(jsonPath("$.data.user.preferredLanguage").value("EN"))
			.andExpect(jsonPath("$.data.signupStatus").value("ACTIVE"))
			.andExpect(jsonPath("$.data.nextStep").value("COMPLETED"))
			.andExpect(jsonPath("$.data.defaultLocationId").value(12))
			.andExpect(jsonPath("$.data.onboardingCompleted").value(true))
			.andExpect(jsonPath("$.data.buddyProfileExists").value(true))
			.andExpect(jsonPath("$.data.unreadMessageCount").value(3))
			.andExpect(jsonPath("$.data.termsNeedReAgreement").value(false));
	}

	@Test
	void returnsUnauthorizedForAStalePrincipal() throws Exception {
		when(service.get("usr_missing")).thenThrow(new UserUnavailableException());

		mockMvc.perform(get("/api/v1/users/me")
				.with(user("usr_missing").roles("USER")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
}
