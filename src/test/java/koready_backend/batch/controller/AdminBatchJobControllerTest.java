package koready_backend.batch.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.batch.application.BatchJobAdminService;
import koready_backend.batch.application.exception.BatchJobNotFoundException;
import koready_backend.batch.domain.BatchItemStatus;
import koready_backend.batch.domain.BatchItemTargetType;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBatchJobControllerTest {

	private static final Instant NOW = Instant.parse("2026-07-19T11:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	BatchJobAdminService service;

	@BeforeEach
	void defaults() {
		when(service.listJobs(any())).thenReturn(
			new BatchJobAdminService.JobPage(List.of(job()), null, false));
		when(service.getJob(7L)).thenReturn(job());
		when(service.listItems(any(Long.class), any())).thenReturn(
			new BatchJobAdminService.ItemPage(7L, List.of(item()), null, false));
	}

	@Test
	void requiresAnAdminReadRole() throws Exception {
		mockMvc.perform(get("/api/v1/admin/batch-jobs"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/admin/batch-jobs")
				.with(user("member").roles("USER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));

		for (String role : List.of("ADMIN", "OPERATOR", "AUDITOR")) {
			mockMvc.perform(get("/api/v1/admin/batch-jobs")
					.with(user(role.toLowerCase()).roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("BATCH_JOB_LIST_OK"));
		}
	}

	@Test
	void returnsActualJobAndItemFieldsOnly() throws Exception {
		mockMvc.perform(get("/api/v1/admin/batch-jobs/7")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("BATCH_JOB_OK"))
			.andExpect(jsonPath("$.data.originalJobId").value(3))
			.andExpect(jsonPath("$.data.requestedByUserId").value(42))
			.andExpect(jsonPath("$.data.parameters.serviceKey").value("***"));

		mockMvc.perform(get("/api/v1/admin/batch-jobs/7/items")
				.with(user("operator").roles("OPERATOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("BATCH_JOB_ITEM_LIST_OK"))
			.andExpect(jsonPath("$.data.items[0].targetType").value("API_PAGE"))
			.andExpect(jsonPath("$.data.items[0].targetId").value("searchFestival2:1"))
			.andExpect(jsonPath("$.data.items[0].attemptCount").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].relatedCallLogId").doesNotExist());
	}

	@Test
	void validatesQueriesAndMapsMissingJobs() throws Exception {
		mockMvc.perform(get("/api/v1/admin/batch-jobs?size=0")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		when(service.getJob(99L)).thenThrow(new BatchJobNotFoundException(99L));
		mockMvc.perform(get("/api/v1/admin/batch-jobs/99")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BATCH_JOB_NOT_FOUND"));
	}

	private static BatchJobAdminService.BatchJobView job() {
		return new BatchJobAdminService.BatchJobView(
			7L,
			BatchJobType.KTO_DAILY_SYNC,
			BatchJobStatus.FAILED,
			BatchTriggerSource.SCHEDULED,
			NOW.minusSeconds(20),
			NOW.minusSeconds(10),
			2,
			1,
			1,
			"Batch job failed.",
			42L,
			3L,
			Map.of("serviceKey", "***", "pageSize", 100),
			NOW.minusSeconds(30),
			NOW.minusSeconds(10));
	}

	private static BatchJobAdminService.BatchItemView item() {
		return new BatchJobAdminService.BatchItemView(
			11L,
			BatchItemTargetType.API_PAGE,
			"searchFestival2:1",
			BatchItemStatus.FAILED,
			"Batch item failed.",
			NOW.minusSeconds(15),
			NOW.minusSeconds(10));
	}
}
