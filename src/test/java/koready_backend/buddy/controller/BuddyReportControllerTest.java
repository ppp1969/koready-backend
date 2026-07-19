package koready_backend.buddy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.buddy.application.BuddyReportService;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.exception.ReportIdempotencyConflictException;
import koready_backend.buddy.application.exception.ReportNotAllowedException;
import koready_backend.buddy.application.exception.ReportTargetNotFoundException;
import koready_backend.buddy.domain.ReportStatus;
import koready_backend.buddy.domain.ReportTargetType;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BuddyReportControllerTest {

	private static final String KEY = "report-key-001";
	private static final Instant CREATED_AT = Instant.parse("2026-07-19T09:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BuddyReportService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/reports")
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(profileBody()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void createsAReportWithTheStandardEnvelope() throws Exception {
		BuddyReportService.CreateReportCommand command = command();
		when(service.create("usr_reporter", KEY, command))
			.thenReturn(new BuddyReportService.ReportResult(
				9001L,
				ReportTargetType.PROFILE,
				"51",
				ReportStatus.RECEIVED,
				CREATED_AT));

		mockMvc.perform(post("/api/v1/reports")
				.with(user("usr_reporter").roles("USER"))
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(profileBody()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("REPORT_CREATED"))
			.andExpect(jsonPath("$.data.reportId").value(9001))
			.andExpect(jsonPath("$.data.targetType").value("PROFILE"))
			.andExpect(jsonPath("$.data.targetId").value("51"))
			.andExpect(jsonPath("$.data.status").value("RECEIVED"))
			.andExpect(jsonPath("$.data.createdAt")
				.value("2026-07-19T09:00:00Z"));
	}

	@Test
	void validatesHeadersTargetIdsAndBodies() throws Exception {
		mockMvc.perform(post("/api/v1/reports")
				.with(user("usr_reporter").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(profileBody()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(post("/api/v1/reports")
				.with(user("usr_reporter").roles("USER"))
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"targetType":"UNKNOWN","targetId":"051","reason":""}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void mapsSafetyIdempotencyAndPrincipalErrors() throws Exception {
		when(service.create("usr_missing", KEY, command()))
			.thenThrow(new BuddyUserUnavailableException());
		when(service.create("usr_hidden", KEY, command()))
			.thenThrow(new ReportTargetNotFoundException());
		when(service.create("usr_self", KEY, command()))
			.thenThrow(new ReportNotAllowedException());
		when(service.create("usr_conflict", KEY, command()))
			.thenThrow(new ReportIdempotencyConflictException());

		perform("usr_missing")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		perform("usr_hidden")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
		perform("usr_self")
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("REPORT_NOT_ALLOWED"));
		perform("usr_conflict")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
	}

	private org.springframework.test.web.servlet.ResultActions perform(String userId)
		throws Exception {
		return mockMvc.perform(post("/api/v1/reports")
			.with(user(userId).roles("USER"))
			.header("Idempotency-Key", KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content(profileBody()));
	}

	private static BuddyReportService.CreateReportCommand command() {
		return new BuddyReportService.CreateReportCommand(
			ReportTargetType.PROFILE, "51", "Impersonation");
	}

	private static String profileBody() {
		return """
			{"targetType":"PROFILE","targetId":"51","reason":"Impersonation"}
			""";
	}
}
