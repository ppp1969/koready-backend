package koready_backend.user.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.UserLanguageService;
import koready_backend.user.application.exception.UserUnavailableException;
import koready_backend.user.domain.NextStep;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserLanguageControllerTest {

	private static final Instant UPDATED_AT = Instant.parse("2026-07-19T03:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserLanguageService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(patch("/api/v1/users/me/language")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"language":"EN"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void updatesLanguageAndReturnsTheServerCalculatedNextStep() throws Exception {
		when(service.update("usr_language", PlaceLanguage.EN))
			.thenReturn(new UserLanguageService.LanguageResult(
				PlaceLanguage.EN, NextStep.ONBOARDING, UPDATED_AT));

		mockMvc.perform(patch("/api/v1/users/me/language")
				.with(user("usr_language").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"language":"EN"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("USER_LANGUAGE_UPDATED"))
			.andExpect(jsonPath("$.data.language").value("EN"))
			.andExpect(jsonPath("$.data.nextStep").value("ONBOARDING"))
			.andExpect(jsonPath("$.data.updatedAt").value("2026-07-19T03:00:00Z"));
	}

	@Test
	void rejectsMissingAndUnknownLanguages() throws Exception {
		mockMvc.perform(patch("/api/v1/users/me/language")
				.with(user("usr_language").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(patch("/api/v1/users/me/language")
				.with(user("usr_language").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"language":"JA"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void returnsUnauthorizedForAStalePrincipal() throws Exception {
		when(service.update("usr_missing", PlaceLanguage.KO))
			.thenThrow(new UserUnavailableException());

		mockMvc.perform(patch("/api/v1/users/me/language")
				.with(user("usr_missing").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"language":"KO"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
}
