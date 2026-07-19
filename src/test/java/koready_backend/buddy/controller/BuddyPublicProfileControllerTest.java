package koready_backend.buddy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.buddy.application.BuddyPublicProfileService;
import koready_backend.buddy.application.exception.BuddyProfileNotFoundException;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.model.BuddyProfileView;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.domain.PlaceLanguage;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BuddyPublicProfileControllerTest {

	private static final String PATH = "/api/v1/buddy-profiles/51";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BuddyPublicProfileService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsAFrontendReadyPublicProfile() throws Exception {
		when(service.getProfile("usr_viewer", 51L)).thenReturn(profile());

		mockMvc.perform(get(PATH).with(user("usr_viewer").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("BUDDY_PROFILE_OK"))
			.andExpect(jsonPath("$.data.profileId").value(51))
			.andExpect(jsonPath("$.data.nickname").value("Target"))
			.andExpect(jsonPath("$.data.availableLanguages[0]").value("EN"))
			.andExpect(jsonPath("$.data.buddyStyles[0]").value("FOODIE"))
			.andExpect(jsonPath("$.data.socialLinks[0].type").value("INSTAGRAM"))
			.andExpect(jsonPath("$.data.socialLinks[0].displayValue").value("@target"))
			.andExpect(jsonPath("$.data.canMessage").value(true))
			.andExpect(jsonPath("$.data.blockedByMe").value(false));
	}

	@Test
	void returnsNotFoundWithoutLeakingTheProfileReason() throws Exception {
		when(service.getProfile("usr_viewer", 51L))
			.thenThrow(new BuddyProfileNotFoundException(51L));

		mockMvc.perform(get(PATH).with(user("usr_viewer").roles("USER")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BUDDY_PROFILE_NOT_FOUND"));
	}

	@Test
	void returnsUnauthorizedForAStalePrincipal() throws Exception {
		when(service.getProfile("usr_missing", 51L))
			.thenThrow(new BuddyUserUnavailableException());

		mockMvc.perform(get(PATH).with(user("usr_missing").roles("USER")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsStandardBadRequestsForInvalidProfileIds() throws Exception {
		mockMvc.perform(get("/api/v1/buddy-profiles/not-a-number")
				.with(user("usr_viewer").roles("USER")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	private static BuddyProfileView profile() {
		return new BuddyProfileView(
			51L,
			"https://cdn.example.com/target.jpg",
			"Target",
			"France",
			List.of(PlaceLanguage.EN, PlaceLanguage.KO),
			KoreanLevel.BEGINNER,
			"Local food fan",
			List.of(BuddyStyle.FOODIE),
			List.of(new BuddySocialLink(SocialLinkType.INSTAGRAM, "@target")),
			true,
			true,
			true,
			true,
			false,
			Instant.parse("2026-07-19T03:00:00Z"));
	}
}
