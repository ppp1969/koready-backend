package koready_backend.externalapi.controller;

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

import koready_backend.externalapi.application.ExternalApiAdminService;
import koready_backend.externalapi.application.exception.ExternalApiCallNotFoundException;
import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.RawSnapshotStatus;
import koready_backend.externalapi.domain.SnapshotRetentionClass;
import koready_backend.externalapi.domain.SnapshotStorageFormat;
import koready_backend.externalapi.domain.SyncCursorType;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminExternalApiControllerTest {

	private static final Instant NOW = Instant.parse("2026-07-19T09:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	ExternalApiAdminService service;

	@BeforeEach
	void defaults() {
		when(service.summary(any(), any(), any())).thenReturn(summary());
		when(service.listCalls(any())).thenReturn(new ExternalApiAdminService.CallPage(
			List.of(call()), null, false));
		when(service.getCall(7L)).thenReturn(call());
		when(service.listSnapshots(any())).thenReturn(new ExternalApiAdminService.SnapshotPage(
			List.of(snapshot()), null, false));
		when(service.getSnapshot(11L)).thenReturn(snapshot());
		when(service.listSyncCursors()).thenReturn(List.of(syncCursor()));
	}

	@Test
	void requiresAuthenticationAndAllowsEveryReadRole() throws Exception {
		mockMvc.perform(get("/api/v1/admin/open-api/summary"))
			.andExpect(status().isUnauthorized());

		for (String role : List.of("ADMIN", "OPERATOR", "AUDITOR")) {
			mockMvc.perform(get("/api/v1/admin/open-api/summary")
					.with(user(role.toLowerCase()).roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("OPEN_API_SUMMARY_OK"));
		}
	}

	@Test
	void returnsCallsAndSnapshotsWithoutRawBodies() throws Exception {
		mockMvc.perform(get("/api/v1/admin/open-api/calls")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("OPEN_API_CALL_LIST_OK"))
			.andExpect(jsonPath("$.data.items[0].callLogId").value(7))
			.andExpect(jsonPath("$.data.items[0].endpoint").doesNotExist());

		mockMvc.perform(get("/api/v1/admin/open-api/calls/7")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.endpoint").value("https://apis.example.com/search"))
			.andExpect(jsonPath("$.data.requestParamsMasked.serviceKey").value("***"));

		mockMvc.perform(get("/api/v1/admin/open-api/snapshots")
				.with(user("operator").roles("OPERATOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("RAW_SNAPSHOT_LIST_OK"));

		mockMvc.perform(get("/api/v1/admin/open-api/snapshots/11")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.downloadable").value(false));
	}

	@Test
	void validatesQueriesAndMapsMissingResources() throws Exception {
		mockMvc.perform(get("/api/v1/admin/open-api/calls?size=0")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		when(service.getCall(99L)).thenThrow(new ExternalApiCallNotFoundException(99L));
		mockMvc.perform(get("/api/v1/admin/open-api/calls/99")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("OPEN_API_CALL_NOT_FOUND"));
	}

	@Test
	void protectsAndReturnsActualSyncCursorFields() throws Exception {
		mockMvc.perform(get("/api/v1/admin/open-api/sync-cursors"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/admin/open-api/sync-cursors")
				.with(user("member").roles("USER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));

		for (String role : List.of("ADMIN", "OPERATOR", "AUDITOR")) {
			mockMvc.perform(get("/api/v1/admin/open-api/sync-cursors")
					.with(user(role.toLowerCase()).roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SYNC_CURSOR_LIST_OK"))
				.andExpect(jsonPath("$.data.items[0].apiName").value("KOR"))
				.andExpect(jsonPath("$.data.items[0].cursorType").value("DATE_RANGE"))
				.andExpect(jsonPath("$.data.items[0].lastErrorCode").doesNotExist())
				.andExpect(jsonPath("$.data.items[0].nextRunAt").doesNotExist());
		}
	}

	private static ExternalApiAdminService.OpenApiSummary summary() {
		return new ExternalApiAdminService.OpenApiSummary(
			new ExternalApiAdminService.Period(NOW.minusSeconds(3600), NOW),
			1,
			1,
			0,
			100.0,
			1,
			List.of(new ExternalApiAdminService.ProviderSummary(
				ExternalApiProvider.KTO, 1, 1, 0, NOW)),
			List.of());
	}

	private static ExternalApiAdminService.CallView call() {
		return new ExternalApiAdminService.CallView(
			7L,
			ExternalApiProvider.KTO,
			"KOR",
			"searchFestival2",
			NOW.minusSeconds(10),
			NOW.minusSeconds(9),
			1000,
			true,
			200,
			"0000",
			1,
			1024,
			RawSnapshotStatus.AVAILABLE,
			12L,
			"https://apis.example.com/search",
			Map.of("serviceKey", "***"),
			new ExternalApiAdminService.ResponseSummary("0000", "OK", 1, 1),
			null,
			new ExternalApiAdminService.RelatedJob(12L, "KTO_DAILY_SYNC"),
			new ExternalApiAdminService.RelatedSnapshot(
				11L,
				RawSnapshotStatus.AVAILABLE,
				"a".repeat(64),
				"b".repeat(64),
				1024,
				false));
	}

	private static ExternalApiAdminService.SnapshotView snapshot() {
		return new ExternalApiAdminService.SnapshotView(
			11L,
			7L,
			ExternalApiProvider.KTO,
			"KOR",
			"searchFestival2",
			"kto/kor/searchFestival2/snapshot.json.gz",
			SnapshotStorageFormat.JSON_GZIP,
			"application/json",
			"a".repeat(64),
			"b".repeat(64),
			1024,
			256,
			1,
			NOW,
			SnapshotRetentionClass.COMPETITION_EVIDENCE,
			null,
			true,
			false);
	}

	private static ExternalApiAdminService.SyncCursorView syncCursor() {
		return new ExternalApiAdminService.SyncCursorView(
			13L,
			ExternalApiProvider.KTO,
			"KOR",
			"searchFestival2",
			SyncCursorType.DATE_RANGE,
			"20260701:3",
			NOW.minusSeconds(30),
			null,
			0,
			true,
			NOW.minusSeconds(60),
			NOW.minusSeconds(30));
	}
}
