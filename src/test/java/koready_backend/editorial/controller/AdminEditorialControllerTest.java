package koready_backend.editorial.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import koready_backend.editorial.application.EditorialService;
import koready_backend.editorial.domain.EditorialJobPriority;
import koready_backend.editorial.domain.EditorialJobStatus;
import koready_backend.editorial.domain.EditorialTriggerType;
import koready_backend.editorial.domain.EditorialCandidateSourceTrack;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminEditorialControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EditorialService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/admin/editorial/candidates"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(roles = "USER")
	void rejectsNonAdmin() throws Exception {
		mockMvc.perform(get("/api/v1/admin/editorial/candidates"))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "admin-subject", roles = "ADMIN")
	void queuesPmSelectionAtHighPriority() throws Exception {
		when(service.enqueueByAdmin(10L, "admin-subject")).thenReturn(
			new EditorialService.JobView(
				"job-1", 10L, EditorialJobStatus.QUEUED,
				EditorialJobPriority.HIGH, EditorialTriggerType.PM_CURATED,
				Instant.parse("2026-08-13T00:00:00Z"), true));

		mockMvc.perform(post("/api/v1/admin/editorial/places/10/queue"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("EDITORIAL_JOB_QUEUED"))
			.andExpect(jsonPath("$.data.priority").value("HIGH"))
			.andExpect(jsonPath("$.data.triggerType").value("PM_CURATED"))
			.andExpect(jsonPath("$.data.created").value(true));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void listsCandidatePlaces() throws Exception {
		when(service.candidates(any(), any(), any(), any(), any(), any(), any(Long.class), any(Integer.class)))
			.thenReturn(new EditorialService.CandidatePage(List.of(), null, false, 0));

		mockMvc.perform(get("/api/v1/admin/editorial/candidates"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items").isArray())
			.andExpect(jsonPath("$.data.hasMore").value(false));
		verify(service).candidates(any(), any(), any(), any(), any(),
			org.mockito.ArgumentMatchers.eq(EditorialCandidateSourceTrack.KTO_BILINGUAL),
			any(Long.class), any(Integer.class));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void acceptsSelectionFiltersAndReturnsTotalCount() throws Exception {
		when(service.candidates(any(), any(), any(), any(), any(), any(), any(Long.class), any(Integer.class)))
			.thenReturn(new EditorialService.CandidatePage(List.of(), null, false, 0));

		mockMvc.perform(get("/api/v1/admin/editorial/candidates")
				.param("query", "4")
				.param("status", "IN_PROGRESS")
				.param("region", "SEOUL")
				.param("hasKoreanOverview", "true")
				.param("queueEligible", "false")
				.param("sourceTrack", "KOREAN_ONLY_AI"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalCount").value(0));
		verify(service).candidates(any(), any(), any(), any(), any(),
			org.mockito.ArgumentMatchers.eq(EditorialCandidateSourceTrack.KOREAN_ONLY_AI),
			any(Long.class), any(Integer.class));
	}

	@Test
	@WithMockUser(username = "admin-subject", roles = "ADMIN")
	void changesPlaceVisibility() throws Exception {
		Instant updatedAt = Instant.parse("2026-08-15T06:00:00Z");
		when(service.updateVisibility(10L, true, "admin-subject")).thenReturn(
			new EditorialService.PlaceVisibilityView(10L, true, true, true, updatedAt));

		mockMvc.perform(patch("/api/v1/admin/editorial/places/10/visibility")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"visible\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("EDITORIAL_PLACE_VISIBILITY_UPDATED"))
			.andExpect(jsonPath("$.data.placeId").value(10))
			.andExpect(jsonPath("$.data.visible").value(true))
			.andExpect(jsonPath("$.data.active").value(true))
			.andExpect(jsonPath("$.data.showFlag").value(true));
	}

	@Test
	@WithMockUser(username = "admin-subject", roles = "ADMIN")
	void changesPlaceCurationPriority() throws Exception {
		Instant updatedAt = Instant.parse("2026-08-25T07:00:00Z");
		when(service.updateCurationPriority(10L, 900, "admin-subject")).thenReturn(
			new EditorialService.PlacePriorityView(10L, 900, updatedAt));

		mockMvc.perform(patch("/api/v1/admin/editorial/places/10/priority")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"priority\":900}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("EDITORIAL_PLACE_PRIORITY_UPDATED"))
			.andExpect(jsonPath("$.data.placeId").value(10))
			.andExpect(jsonPath("$.data.priority").value(900));
	}

	@Test
	@WithMockUser(username = "admin-subject", roles = "ADMIN")
	void reordersPlaceImagesAndSelectsTheFirstAsThumbnail() throws Exception {
		Instant updatedAt = Instant.parse("2026-08-25T07:00:00Z");
		when(service.reorderImages(10L, List.of(103L, 101L), "admin-subject"))
			.thenReturn(new EditorialService.PlaceImageOrderView(
				10L,
				List.of(
					new EditorialService.PlaceImageView(103L, "https://img/3.jpg", 1, true),
					new EditorialService.PlaceImageView(101L, "https://img/1.jpg", 2, false)),
				updatedAt));

		mockMvc.perform(put("/api/v1/admin/editorial/places/10/images/order")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"imageIds\":[103,101]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("EDITORIAL_PLACE_IMAGES_REORDERED"))
			.andExpect(jsonPath("$.data.images[0].imageId").value(103))
			.andExpect(jsonPath("$.data.images[0].thumbnail").value(true))
			.andExpect(jsonPath("$.data.images[1].displayOrder").value(2));
	}
}
