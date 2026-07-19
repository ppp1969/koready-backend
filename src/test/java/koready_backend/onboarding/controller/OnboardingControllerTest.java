package koready_backend.onboarding.controller;

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

import koready_backend.onboarding.application.OnboardingService;
import koready_backend.onboarding.application.OnboardingService.CompletionResult;
import koready_backend.onboarding.application.OnboardingService.LocationResult;
import koready_backend.onboarding.application.OnboardingService.ProfileResult;
import koready_backend.onboarding.application.OnboardingService.ProgressResult;
import koready_backend.onboarding.application.exception.OnboardingCompletionException;
import koready_backend.onboarding.application.exception.OnboardingCompletionException.Reason;
import koready_backend.onboarding.domain.OnboardingStep;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.user.domain.NextStep;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingControllerTest {

	private static final Instant COMPLETED_AT = Instant.parse("2026-07-19T05:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OnboardingService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/users/me/onboarding"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsProgressThatTheClientCanRestore() throws Exception {
		when(service.getProgress("usr_onboarding")).thenReturn(new ProgressResult(
			false,
			OnboardingStep.PREFERENCE_PLACES,
			11L,
			List.of(TravelStyle.LOCAL_FOOD),
			null,
			null,
			List.of()));

		mockMvc.perform(get("/api/v1/users/me/onboarding")
				.with(user("usr_onboarding").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ONBOARDING_PROGRESS_OK"))
			.andExpect(jsonPath("$.data.completed").value(false))
			.andExpect(jsonPath("$.data.currentStep").value("PREFERENCE_PLACES"))
			.andExpect(jsonPath("$.data.currentLocationId").value(11))
			.andExpect(jsonPath("$.data.travelStyles[0]").value("LOCAL_FOOD"));
	}

	@Test
	void completesAndReturnsTheStoredProfile() throws Exception {
		when(service.complete(org.mockito.ArgumentMatchers.eq("usr_onboarding"),
			org.mockito.ArgumentMatchers.any())).thenReturn(new CompletionResult(
			true,
			COMPLETED_AT,
			NextStep.COMPLETED,
			new ProfileResult(
				new LocationResult(11L, "성신여자대학교", ServiceRegionCode.SEOUL),
				List.of(TravelStyle.LOCAL_FOOD),
				List.of(101L),
				List.of())));

		mockMvc.perform(put("/api/v1/users/me/onboarding")
				.with(user("usr_onboarding").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "currentLocationId": 11,
					  "travelStyles": ["LOCAL_FOOD"],
					  "candidateSetId": "onb-v1",
					  "candidateSetVersion": 1,
					  "selectedPreferencePlaceIds": [101]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ONBOARDING_COMPLETED"))
			.andExpect(jsonPath("$.data.completed").value(true))
			.andExpect(jsonPath("$.data.completedAt").value("2026-07-19T05:00:00Z"))
			.andExpect(jsonPath("$.data.profile.currentLocation.locationId").value(11))
			.andExpect(jsonPath("$.data.profile.preferenceTags").isEmpty());
	}

	@Test
	void rejectsMalformedAndDuplicateRequests() throws Exception {
		mockMvc.perform(put("/api/v1/users/me/onboarding")
				.with(user("usr_onboarding").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		when(service.complete(org.mockito.ArgumentMatchers.eq("usr_onboarding"),
			org.mockito.ArgumentMatchers.any()))
			.thenThrow(new OnboardingCompletionException(
				Reason.TRAVEL_STYLES_INVALID, "Travel styles must be unique."));

		mockMvc.perform(put("/api/v1/users/me/onboarding")
				.with(user("usr_onboarding").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "currentLocationId": 11,
					  "travelStyles": ["NATURE", "NATURE"],
					  "candidateSetId": "onb-v1",
					  "candidateSetVersion": 1,
					  "selectedPreferencePlaceIds": [101]
					}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("ONBOARDING_TRAVEL_STYLES_INVALID"));
	}

	@Test
	void returnsConflictWhenACompletedProfileWouldBeOverwritten() throws Exception {
		when(service.complete(org.mockito.ArgumentMatchers.eq("usr_onboarding"),
			org.mockito.ArgumentMatchers.any()))
			.thenThrow(new OnboardingCompletionException(
				Reason.ALREADY_COMPLETED, "Onboarding is already completed."));

		mockMvc.perform(put("/api/v1/users/me/onboarding")
				.with(user("usr_onboarding").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "currentLocationId": 11,
					  "travelStyles": ["NATURE"],
					  "candidateSetId": "onb-v1",
					  "candidateSetVersion": 1,
					  "selectedPreferencePlaceIds": [101]
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ONBOARDING_ALREADY_COMPLETED"));
	}
}
