package koready_backend.buddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.buddy.application.BuddyProfileService;
import koready_backend.buddy.application.model.BuddyProfileView;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.buddy.domain.ProfileLanguage;
import koready_backend.place.domain.TravelStyle;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BuddyProfileControllerTest {

	private static final Instant UPDATED_AT = Instant.parse("2026-07-19T03:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BuddyProfileService service;

	@Test
	void requiresAuthenticationForReadAndWrite() throws Exception {
		mockMvc.perform(get("/api/v1/users/me/buddy-profile"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(put("/api/v1/users/me/buddy-profile")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsAnAbsentProfileAsAStableEditingState() throws Exception {
		when(service.getMyProfile("usr_new"))
			.thenReturn(new BuddyProfileService.MyProfileResult(false, null));

		mockMvc.perform(get("/api/v1/users/me/buddy-profile")
				.with(user("usr_new").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("BUDDY_PROFILE_OK"))
			.andExpect(jsonPath("$.data.exists").value(false))
			.andExpect(jsonPath("$.data.profile").doesNotExist());
	}

	@Test
	void savesAndReturnsTheFullProfile() throws Exception {
		when(service.upsertMyProfile(eq("usr_emma"), any()))
			.thenReturn(profile());

		mockMvc.perform(put("/api/v1/users/me/buddy-profile")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("BUDDY_PROFILE_SAVED"))
			.andExpect(jsonPath("$.data.profileId").value(51))
			.andExpect(jsonPath("$.data.nickname").value("Emma"))
			.andExpect(jsonPath("$.data.nationalityCode").value("FR"))
			.andExpect(jsonPath("$.data.availableLanguages[0]").value("VI"))
			.andExpect(jsonPath("$.data.koreanLevel").value("BEGINNER"))
			.andExpect(jsonPath("$.data.travelStyles[0]").value("LOCAL_FOOD"))
			.andExpect(jsonPath("$.data.buddyStyles[0]").value("FOODIE"))
			.andExpect(jsonPath("$.data.socialLinks[0].type").value("INSTAGRAM"))
			.andExpect(jsonPath("$.data.socialLinks[0].displayValue").value("@emma"))
			.andExpect(jsonPath("$.data.socialLinks[0].url").doesNotExist())
			.andExpect(jsonPath("$.data.canMessage").value(false))
			.andExpect(jsonPath("$.data.blockedByMe").value(false));
	}

	@Test
	void acceptsFourTravelStylesAndRejectsFiveTravelStylesOrThreeSocialLinks() throws Exception {
		when(service.upsertMyProfile(eq("usr_emma"), any()))
			.thenReturn(profile());
		String fourTravelStyles = """
			["LOCAL_FOOD","LOCAL_FESTIVAL","TRADITIONAL_MARKET","CULTURE_EXPERIENCE"]
			""".replaceAll("\\s+", "");

		mockMvc.perform(put("/api/v1/users/me/buddy-profile")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace(
					"[\"LOCAL_FOOD\", \"CULTURE_EXPERIENCE\"]",
					fourTravelStyles)))
			.andExpect(status().isOk());

		String fiveTravelStyles = """
			["LOCAL_FOOD","LOCAL_FESTIVAL","TRADITIONAL_MARKET",
			 "CULTURE_EXPERIENCE","NATURE"]
			""".replaceAll("\\s+", "");
		mockMvc.perform(put("/api/v1/users/me/buddy-profile")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace(
					"[\"LOCAL_FOOD\", \"CULTURE_EXPERIENCE\"]",
					fiveTravelStyles)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(put("/api/v1/users/me/buddy-profile")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace(
					"[{\"type\":\"INSTAGRAM\",\"value\":\"@emma\"}]",
					"""
					[
					  {"type":"INSTAGRAM","value":"@emma"},
					  {"type":"KAKAOTALK","value":"emma"},
					  {"type":"TIKTOK","value":"@emma"}
					]
					""")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsInvalidEnumsBlankNicknamesAndOversizedBios() throws Exception {
		mockMvc.perform(put("/api/v1/users/me/buddy-profile")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("\"Emma\"", "\"   \"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(put("/api/v1/users/me/buddy-profile")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("BEGINNER", "NATIVE")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		String longBio = "x".repeat(121);
		mockMvc.perform(put("/api/v1/users/me/buddy-profile")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("Local food fan", longBio)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void returnsUnauthorizedForAStalePrincipal() throws Exception {
		when(service.getMyProfile("usr_missing"))
			.thenThrow(new BuddyUserUnavailableException());

		mockMvc.perform(get("/api/v1/users/me/buddy-profile")
				.with(user("usr_missing").roles("USER")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private static BuddyProfileView profile() {
		return new BuddyProfileView(
			51L,
			"/api/v1/profile-images/img_11111111222233334444555555555555",
			"Emma",
			"FR",
			List.of(ProfileLanguage.VI, ProfileLanguage.KO),
			KoreanLevel.BEGINNER,
			List.of(TravelStyle.LOCAL_FOOD, TravelStyle.CULTURE_EXPERIENCE),
			"Local food fan",
			List.of(BuddyStyle.FOODIE),
			List.of(new BuddySocialLink(SocialLinkType.INSTAGRAM, "@emma")),
			true,
			true,
			true,
			false,
			false,
			UPDATED_AT);
	}

	private static String validRequest() {
		return """
			{
			  "profileImageUrl": null,
			  "nickname": "Emma",
			  "nationalityCode": "FR",
			  "availableLanguages": ["VI", "KO"],
			  "koreanLevel": "BEGINNER",
			  "travelStyles": ["LOCAL_FOOD", "CULTURE_EXPERIENCE"],
			  "bio": "Local food fan",
			  "buddyStyles": ["FOODIE"],
			  "socialLinks": [{"type":"INSTAGRAM","value":"@emma"}],
			  "profilePublic": true,
			  "snsPublic": true,
			  "allowsMessages": true
			}
			""";
	}
}
