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

import koready_backend.buddy.application.BuddyMateService;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.exception.InvalidMateCursorException;
import koready_backend.buddy.application.model.BuddyProfileView;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.application.exception.PlaceNotFoundException;
import koready_backend.place.domain.PlaceLanguage;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BuddyMateControllerTest {

	private static final String PATH = "/api/v1/places/101/mates";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BuddyMateService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsAFrontendReadyCursorPage() throws Exception {
		when(service.getMates("usr_viewer", 101L, null, 20)).thenReturn(
			new BuddyMateService.PlaceMatePage(
				101L,
				List.of(profile()),
				"next-cursor",
				true));

		mockMvc.perform(get(PATH).with(user("usr_viewer").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("PLACE_MATE_LIST_OK"))
			.andExpect(jsonPath("$.data.placeId").value(101))
			.andExpect(jsonPath("$.data.items[0].profileId").value(51))
			.andExpect(jsonPath("$.data.items[0].nickname").value("Target"))
			.andExpect(jsonPath("$.data.items[0].socialLinks[0].displayValue")
				.value("@target"))
			.andExpect(jsonPath("$.data.items[0].canMessage").value(true))
			.andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
			.andExpect(jsonPath("$.data.hasMore").value(true));
	}

	@Test
	void returnsDocumentedCursorPlaceAndPrincipalErrors() throws Exception {
		when(service.getMates("usr_viewer", 101L, "bad", 20))
			.thenThrow(new InvalidMateCursorException());
		mockMvc.perform(get(PATH)
				.param("cursor", "bad")
				.with(user("usr_viewer").roles("USER")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));

		when(service.getMates("usr_viewer", 101L, null, 20))
			.thenThrow(new PlaceNotFoundException(101L));
		mockMvc.perform(get(PATH).with(user("usr_viewer").roles("USER")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));

		when(service.getMates("usr_stale", 101L, null, 20))
			.thenThrow(new BuddyUserUnavailableException());
		mockMvc.perform(get(PATH).with(user("usr_stale").roles("USER")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void validatesPositivePlaceIdsCursorLengthAndPageSize() throws Exception {
		mockMvc.perform(get("/api/v1/places/0/mates")
				.with(user("usr_viewer").roles("USER")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(get(PATH)
				.param("cursor", "x".repeat(513))
				.with(user("usr_viewer").roles("USER")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(get(PATH)
				.param("size", "51")
				.with(user("usr_viewer").roles("USER")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	private static BuddyProfileView profile() {
		return new BuddyProfileView(
			51L,
			null,
			"Target",
			"France",
			List.of(PlaceLanguage.EN),
			KoreanLevel.BEGINNER,
			"Travel mate",
			List.of(BuddyStyle.FOODIE),
			List.of(new BuddySocialLink(SocialLinkType.INSTAGRAM, "@target")),
			true,
			true,
			true,
			true,
			false,
			Instant.parse("2026-07-19T02:00:00Z"));
	}
}
