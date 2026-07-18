package koready_backend.horitip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import koready_backend.horitip.application.HoriTipService;
import koready_backend.horitip.application.exception.HoriTipConcurrentModificationException;
import koready_backend.horitip.application.exception.HoriTipNotFoundException;
import koready_backend.horitip.domain.HoriTipDraft;
import koready_backend.horitip.domain.HoriTipPlacement;
import koready_backend.horitip.domain.HoriTipRouteMode;
import koready_backend.horitip.domain.HoriTipScope;
import koready_backend.horitip.domain.HoriTipScopeType;
import koready_backend.horitip.domain.HoriTipStatus;
import koready_backend.horitip.domain.HoriTipStatusTarget;
import koready_backend.horitip.domain.HoriTipTranslation;
import koready_backend.horitip.domain.HoriTipTrigger;
import koready_backend.place.domain.PlaceLanguage;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminHoriTipControllerTest {

	private static final Instant NOW = Instant.parse("2026-07-19T08:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	HoriTipService service;

	@BeforeEach
	void defaults() {
		when(service.list(any(), any(), any(), any(), anyInt(), anyBoolean()))
			.thenReturn(new HoriTipService.HoriTipPage(List.of(view()), null, false));
		when(service.create(any(), eq("admin"), eq(true))).thenReturn(view());
		when(service.get(7L, true)).thenReturn(view());
		when(service.update(eq(7L), any(), eq("admin"), eq(true))).thenReturn(view());
		when(service.changeStatus(eq(7L), any(), eq("admin"), eq(true))).thenReturn(view());
	}

	@Test
	void requiresAuthenticationAndWriteRole() throws Exception {
		mockMvc.perform(get("/api/v1/admin/hori-tips"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/admin/hori-tips")
				.with(user("auditor").roles("AUDITOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody()))
			.andExpect(status().isForbidden());
	}

	@Test
	void letsAnAuditorReadButNotEdit() throws Exception {
		when(service.list(any(), any(), any(), any(), anyInt(), eq(false)))
			.thenReturn(new HoriTipService.HoriTipPage(List.of(view(false)), null, false));

		mockMvc.perform(get("/api/v1/admin/hori-tips")
				.with(user("auditor").roles("AUDITOR")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].editable").value(false));
	}

	@Test
	void createsReadsUpdatesAndChangesStatus() throws Exception {
		mockMvc.perform(post("/api/v1/admin/hori-tips")
				.with(user("admin").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("ADMIN_HORI_TIP_CREATED"))
			.andExpect(jsonPath("$.data.horiTipId").value(7))
			.andExpect(jsonPath("$.data.source").value("OPERATOR_CURATED"))
			.andExpect(jsonPath("$.data.translations[0].title").value("Hori Tip"));

		mockMvc.perform(get("/api/v1/admin/hori-tips/7")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ADMIN_HORI_TIP_OK"));

		mockMvc.perform(put("/api/v1/admin/hori-tips/7")
				.with(user("admin").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateBody()))
			.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/admin/hori-tips/7/status")
				.with(user("admin").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"status":"ACTIVE","version":1,"reason":"Reviewed"}
					"""))
			.andExpect(status().isOk());
	}

	@Test
	void validatesBodiesAndMapsVersionConflicts() throws Exception {
		mockMvc.perform(post("/api/v1/admin/hori-tips")
				.with(user("admin").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"code":"invalid"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		when(service.update(eq(7L), any(), eq("admin"), eq(true)))
			.thenThrow(new HoriTipConcurrentModificationException());
		mockMvc.perform(put("/api/v1/admin/hori-tips/7")
				.with(user("admin").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateBody()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HORI_TIP_NOT_EDITABLE"));

		when(service.get(99L, true)).thenThrow(new HoriTipNotFoundException(99L));
		mockMvc.perform(get("/api/v1/admin/hori-tips/99")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("HORI_TIP_NOT_FOUND"));
	}

	private static HoriTipService.HoriTipView view() {
		return view(true);
	}

	private static HoriTipService.HoriTipView view(boolean editable) {
		return new HoriTipService.HoriTipView(
			7L,
			"TIP_STATION",
			"OPERATOR_CURATED",
			HoriTipStatus.DRAFT,
			draft(),
			1,
			false,
			editable,
			"admin",
			"admin",
			null,
			null,
			NOW,
			NOW);
	}

	private static HoriTipDraft draft() {
		return new HoriTipDraft(
			HoriTipPlacement.AFTER_SEGMENT,
			100,
			new HoriTipScope(HoriTipScopeType.DESTINATION_PLACES, List.of(10L)),
			new HoriTipTrigger(
				List.of(HoriTipRouteMode.TRAIN),
				List.of("KTX"),
				List.of(),
				List.of("Gimcheon"),
				3600,
				null,
				null),
			List.of(new HoriTipTranslation(PlaceLanguage.KO, "Korean body")),
			null,
			null,
			"Operator note");
	}

	private static String createBody() {
		return editableBody("\"code\":\"TIP_STATION\",");
	}

	private static String updateBody() {
		return editableBody("\"version\":1,");
	}

	private static String editableBody(String firstField) {
		return """
			{
			  %s
			  "placement":"AFTER_SEGMENT",
			  "priority":100,
			  "scope":{"scopeType":"DESTINATION_PLACES","destinationPlaceIds":[10]},
			  "trigger":{
			    "segmentModes":["TRAIN"],
			    "routeNameContainsAny":["KTX"],
			    "segmentStartNameContainsAny":[],
			    "segmentEndNameContainsAny":["Gimcheon"],
			    "minProviderTotalTimeSeconds":3600,
			    "minTransferCount":null,
			    "minTotalWalkDistanceMeters":null
			  },
			  "translations":[{"language":"KO","body":"Korean body"}],
			  "validFrom":null,
			  "validUntil":null,
			  "operatorNote":"Operator note"
			}
			""".formatted(firstField);
	}
}
