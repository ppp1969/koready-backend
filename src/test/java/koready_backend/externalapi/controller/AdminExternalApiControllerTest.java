package koready_backend.externalapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.externalapi.application.ExternalApiAdminService;
import koready_backend.externalapi.application.exception.ExternalApiCallNotFoundException;
import koready_backend.externalapi.application.exception.ProviderRetentionRestrictedException;
import koready_backend.externalapi.application.exception.RawSnapshotDownloadUnavailableException;
import koready_backend.externalapi.application.exception.RawSnapshotExpiredException;
import koready_backend.externalapi.application.exception.RawSnapshotNotFoundException;
import koready_backend.externalapi.application.exception.SyncCursorNotFoundException;
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
		when(service.createSnapshotDownloadUrl(anyLong(), anyString()))
			.thenReturn(new ExternalApiAdminService.SnapshotDownloadView(
				"https://private-storage.example/snapshot?signature=temporary",
				NOW.plusSeconds(300),
				"koready-kto-snapshot-11.json.gz",
				"a".repeat(64),
				"b".repeat(64)));
		when(service.listSyncCursors()).thenReturn(List.of(syncCursor()));
		when(service.updateSyncCursorEnabled(
			anyLong(), anyBoolean(), anyString(), anyString()))
			.thenReturn(syncCursor(false, "20260701:3"));
		when(service.resetSyncCursor(anyLong(), anyString(), anyString(), anyString()))
			.thenReturn(syncCursor(true, "20260601:1"));
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
	void protectsAndIssuesShortLivedSnapshotDownloadUrls() throws Exception {
		mockMvc.perform(post("/api/v1/admin/open-api/snapshots/11/download-url"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/admin/open-api/snapshots/11/download-url")
				.with(user("member").roles("USER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));

		for (String role : List.of("ADMIN", "OPERATOR", "AUDITOR")) {
			mockMvc.perform(post("/api/v1/admin/open-api/snapshots/11/download-url")
					.with(user(role.toLowerCase()).roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("RAW_SNAPSHOT_DOWNLOAD_URL_ISSUED"))
				.andExpect(jsonPath("$.data.downloadUrl").isString())
				.andExpect(jsonPath("$.data.expiresAt").value("2026-07-19T09:05:00Z"))
				.andExpect(jsonPath("$.data.fileName")
					.value("koready-kto-snapshot-11.json.gz"))
				.andExpect(jsonPath("$.data.rawContentSha256").value("a".repeat(64)))
				.andExpect(jsonPath("$.data.storedObjectSha256").value("b".repeat(64)));
		}
	}

	@Test
	void mapsSnapshotDownloadPolicyAndAvailabilityFailures() throws Exception {
		when(service.createSnapshotDownloadUrl(20L, "admin-1"))
			.thenThrow(new RawSnapshotNotFoundException(20L));
		mockMvc.perform(post("/api/v1/admin/open-api/snapshots/20/download-url")
				.with(user("admin-1").roles("ADMIN")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RAW_SNAPSHOT_NOT_FOUND"));

		when(service.createSnapshotDownloadUrl(21L, "admin-1"))
			.thenThrow(new RawSnapshotExpiredException(21L));
		mockMvc.perform(post("/api/v1/admin/open-api/snapshots/21/download-url")
				.with(user("admin-1").roles("ADMIN")))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("RAW_SNAPSHOT_EXPIRED"));

		when(service.createSnapshotDownloadUrl(22L, "admin-1"))
			.thenThrow(new ProviderRetentionRestrictedException(22L));
		mockMvc.perform(post("/api/v1/admin/open-api/snapshots/22/download-url")
				.with(user("admin-1").roles("ADMIN")))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code")
				.value("PROVIDER_RETENTION_RESTRICTED"));

		when(service.createSnapshotDownloadUrl(23L, "admin-1"))
			.thenThrow(new RawSnapshotDownloadUnavailableException());
		mockMvc.perform(post("/api/v1/admin/open-api/snapshots/23/download-url")
				.with(user("admin-1").roles("ADMIN")))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code")
				.value("RAW_SNAPSHOT_DOWNLOAD_UNAVAILABLE"));
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

	@Test
	void onlyAdminCanChangeSyncCursorState() throws Exception {
		mockMvc.perform(put("/api/v1/admin/open-api/sync-cursors/13/enabled")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":false,\"reason\":\"야간 점검\"}"))
			.andExpect(status().isUnauthorized());

		for (String role : List.of("OPERATOR", "AUDITOR", "USER")) {
			mockMvc.perform(put("/api/v1/admin/open-api/sync-cursors/13/enabled")
					.with(user(role.toLowerCase()).roles(role))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"enabled\":false,\"reason\":\"야간 점검\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));
		}

		mockMvc.perform(put("/api/v1/admin/open-api/sync-cursors/13/enabled")
				.with(user("admin-1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":false,\"reason\":\"야간 점검\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SYNC_CURSOR_UPDATED"))
			.andExpect(jsonPath("$.data.enabled").value(false));

		mockMvc.perform(post("/api/v1/admin/open-api/sync-cursors/13/reset")
				.with(user("admin-1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cursorValue\":\"20260601:1\","
					+ "\"reason\":\"누락 기간 재수집\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SYNC_CURSOR_RESET"))
			.andExpect(jsonPath("$.data.cursorValue").value("20260601:1"));
	}

	@Test
	void validatesSyncCursorWritesAndMapsMissingCursor() throws Exception {
		mockMvc.perform(put("/api/v1/admin/open-api/sync-cursors/13/enabled")
				.with(user("admin-1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"enabled 누락\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(post("/api/v1/admin/open-api/sync-cursors/13/reset")
				.with(user("admin-1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cursorValue\":\" \",\"reason\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		when(service.resetSyncCursor(
			99L, "20260601:1", "누락 기간 재수집", "admin-1"))
			.thenThrow(new SyncCursorNotFoundException(99L));
		mockMvc.perform(post("/api/v1/admin/open-api/sync-cursors/99/reset")
				.with(user("admin-1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cursorValue\":\"20260601:1\","
					+ "\"reason\":\"누락 기간 재수집\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SYNC_CURSOR_NOT_FOUND"));
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
		return syncCursor(true, "20260701:3");
	}

	private static ExternalApiAdminService.SyncCursorView syncCursor(
		boolean enabled,
		String cursorValue
	) {
		return new ExternalApiAdminService.SyncCursorView(
			13L,
			ExternalApiProvider.KTO,
			"KOR",
			"searchFestival2",
			SyncCursorType.DATE_RANGE,
			cursorValue,
			NOW.minusSeconds(30),
			null,
			0,
			enabled,
			NOW.minusSeconds(60),
			NOW.minusSeconds(30));
	}
}
