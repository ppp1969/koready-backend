package koready_backend.kto.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.kto.application.KtoPhotoGalleryCurationService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminKtoPhotoGalleryControllerTest {

	private static final Instant NOW =
		Instant.parse("2026-07-27T05:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	KtoPhotoGalleryCurationService service;

	@BeforeEach
	void defaults() {
		when(service.list(isNull(), isNull(), anyLong(), anyInt()))
			.thenReturn(
				new KtoPhotoGalleryCurationService.PhotoGalleryPage(
					List.of(image(null)), "10", true));
		when(service.approveMapping(anyString(), any(), anyString()))
			.thenReturn(image(205L));
	}

	@Test
	void listsGalleryCandidatesForAdminReadRoles() throws Exception {
		mockMvc.perform(get("/api/v1/admin/kto/photo-gallery"))
			.andExpect(status().isUnauthorized());

		for (String role : List.of("ADMIN", "OPERATOR", "AUDITOR")) {
			mockMvc.perform(get("/api/v1/admin/kto/photo-gallery")
					.with(user(role.toLowerCase()).roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code")
					.value("KTO_PHOTO_GALLERY_LIST_OK"))
				.andExpect(jsonPath("$.data.items[0].contentId")
					.value("gallery-001"))
				.andExpect(jsonPath("$.data.items[0].rightsStatus")
					.value("REQUIRES_REVIEW"))
				.andExpect(jsonPath("$.data.items[0].mappedPlaceId")
					.doesNotExist());
		}
	}

	@Test
	void allowsOperatorsToApproveAndRemoveMappings()
		throws Exception {
		String approveBody = """
			{
			  "placeId": 205,
			  "displayOrder": 2,
			  "reason": "The operator verified the place and usage rights."
			}
			""";

		mockMvc.perform(put(
				"/api/v1/admin/kto/photo-gallery/gallery-001/mapping")
				.with(user("operator-1").roles("OPERATOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(approveBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code")
				.value("KTO_PHOTO_GALLERY_MAPPING_APPROVED"))
			.andExpect(jsonPath("$.data.mappedPlaceId").value(205))
			.andExpect(jsonPath("$.data.displayOrder").value(2));

		mockMvc.perform(delete(
				"/api/v1/admin/kto/photo-gallery/gallery-001/mapping")
				.with(user("operator-1").roles("OPERATOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reason":"The approved mapping was incorrect."}
					"""))
			.andExpect(status().isNoContent());

		verify(service).removeMapping(
			"gallery-001",
			"The approved mapping was incorrect.",
			"operator-1");
	}

	private KtoPhotoGalleryCurationService.PhotoGalleryView image(
		Long mappedPlaceId
	) {
		return new KtoPhotoGalleryCurationService.PhotoGalleryView(
			"gallery-001",
			"17",
			"Palace in autumn",
			"Seoul",
			"10",
			"KTO",
			"palace,autumn,seoul",
			"https://example.invalid/photo-gallery/gallery-001.jpg",
			"REQUIRES_REVIEW",
			mappedPlaceId,
			mappedPlaceId == null ? null : "Gyeongbokgung Palace",
			mappedPlaceId == null ? null : 2,
			mappedPlaceId == null ? null : "operator-1",
			mappedPlaceId == null
				? null
				: "The operator verified the place and usage rights.",
			mappedPlaceId == null ? null : NOW,
			NOW);
	}
}
