package koready_backend.editorial.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import koready_backend.editorial.application.EditorialService;
import koready_backend.editorial.domain.EditorialJobPriority;
import koready_backend.editorial.domain.EditorialJobStatus;
import koready_backend.editorial.domain.EditorialTriggerType;

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
		when(service.candidates(any(), any(), any(Long.class), any(Integer.class)))
			.thenReturn(new EditorialService.CandidatePage(List.of(), null, false));

		mockMvc.perform(get("/api/v1/admin/editorial/candidates"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items").isArray())
			.andExpect(jsonPath("$.data.hasMore").value(false));
	}
}
