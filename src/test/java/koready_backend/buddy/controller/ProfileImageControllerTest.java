package koready_backend.buddy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.buddy.application.ProfileImageService;
import koready_backend.buddy.application.exception.ProfileImageStorageUnavailableException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileImageControllerTest {

	private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProfileImageService service;

	@Test
	void requiresAuthenticationForUploadReservationAndCompletion() throws Exception {
		mockMvc.perform(post("/api/v1/users/me/profile-image/upload-url")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"contentType\":\"image/jpeg\",\"size\":1024}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/users/me/profile-image/complete")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"imageId":"img_11111111222233334444555555555555"}
					"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void reservesAndCompletesAProfileImage() throws Exception {
		when(service.reserve("usr_emma", "image/jpeg", 1024L))
			.thenReturn(new ProfileImageService.UploadReservation(
				"img_11111111222233334444555555555555",
				"https://signed.example/upload",
				NOW.plusSeconds(600),
				Map.of("Content-Type", "image/jpeg")));
		when(service.complete(
			"usr_emma", "img_11111111222233334444555555555555"))
			.thenReturn(new ProfileImageService.CompletedImage(
				"img_11111111222233334444555555555555",
				"/api/v1/profile-images/img_11111111222233334444555555555555",
				1024L,
				NOW));

		mockMvc.perform(post("/api/v1/users/me/profile-image/upload-url")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"contentType\":\"image/jpeg\",\"size\":1024}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("PROFILE_IMAGE_UPLOAD_RESERVED"))
			.andExpect(jsonPath("$.data.requiredHeaders.Content-Type")
				.value("image/jpeg"));

		mockMvc.perform(post("/api/v1/users/me/profile-image/complete")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"imageId":"img_11111111222233334444555555555555"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("PROFILE_IMAGE_UPLOAD_COMPLETED"))
			.andExpect(jsonPath("$.data.profileImageUrl").value(
				"/api/v1/profile-images/img_11111111222233334444555555555555"));
	}

	@Test
	void rejectsUnsupportedContentAndOversizedFilesAtTheHttpBoundary() throws Exception {
		mockMvc.perform(post("/api/v1/users/me/profile-image/upload-url")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"contentType\":\"image/gif\",\"size\":1024}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/users/me/profile-image/upload-url")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"contentType\":\"image/png\",\"size\":5242881}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void redirectsAReadyPublicImageAndReturnsNotFoundOtherwise() throws Exception {
		when(service.viewUrl(
			"img_11111111222233334444555555555555", null))
			.thenReturn(Optional.of("https://signed.example/view"));

		mockMvc.perform(get(
				"/api/v1/profile-images/img_11111111222233334444555555555555"))
			.andExpect(status().isTemporaryRedirect())
			.andExpect(header().string("Location", "https://signed.example/view"));

		mockMvc.perform(get(
				"/api/v1/profile-images/img_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
			.andExpect(status().isNotFound());
	}

	@Test
	void passesTheAuthenticatedViewerWhenResolvingAPrivateImage() throws Exception {
		when(service.viewUrl(
			"img_11111111222233334444555555555555", "usr_emma"))
			.thenReturn(Optional.of("https://signed.example/private-view"));

		mockMvc.perform(get(
				"/api/v1/profile-images/img_11111111222233334444555555555555")
				.with(user("usr_emma").roles("USER")))
			.andExpect(status().isTemporaryRedirect())
			.andExpect(header().string(
				"Location", "https://signed.example/private-view"));
	}

	@Test
	void reportsUnavailableStorageWithoutLeakingProviderDetails() throws Exception {
		when(service.reserve("usr_emma", "image/jpeg", 1024L))
			.thenThrow(new ProfileImageStorageUnavailableException());

		mockMvc.perform(post("/api/v1/users/me/profile-image/upload-url")
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"contentType\":\"image/jpeg\",\"size\":1024}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code")
				.value("PROFILE_IMAGE_STORAGE_UNAVAILABLE"));
	}
}
