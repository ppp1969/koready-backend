package koready_backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.batch.application.BatchJobAdminService;

@SpringBootTest(properties = {
	"koready.security.staging-operator.enabled=true",
	"koready.security.staging-operator.token=test-staging-operator-token"
})
@AutoConfigureMockMvc
@ActiveProfiles({"staging", "test"})
class StagingOperatorAuthenticationControllerTest {
	private static final String HEADER_NAME = "X-Koready-Operator-Token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BatchJobAdminService batchJobAdminService;

	@Test
	void stagingOperatorTokenCanReadAdminBatchJobs() throws Exception {
		org.mockito.Mockito.when(batchJobAdminService.listJobs(org.mockito.ArgumentMatchers.any()))
			.thenReturn(new BatchJobAdminService.JobPage(List.of(), null, false));

		mockMvc.perform(get("/api/v1/admin/batch-jobs")
				.header(HEADER_NAME, "test-staging-operator-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("BATCH_JOB_LIST_OK"));
	}

	@Test
	void missingOrIncorrectTokenCannotAccessAdminBatchJobs() throws Exception {
		mockMvc.perform(get("/api/v1/admin/batch-jobs"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/admin/batch-jobs")
				.header(HEADER_NAME, "incorrect-token"))
			.andExpect(status().isUnauthorized());
	}
}
