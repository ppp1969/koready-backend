package koready_backend.place.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.place.application.SavedPlaceService;
import koready_backend.place.application.exception.PlaceNotFoundException;
import koready_backend.place.application.exception.SavedPlaceUserUnavailableException;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.SavedPlaceSource;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SavedPlaceControllerTest {

	private static final Instant SAVED_AT = Instant.parse("2026-07-19T03:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SavedPlaceService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/users/me/saved-places"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void savesAPlaceWithItsUiSource() throws Exception {
		when(service.save("usr_saved", 1001L, SavedPlaceSource.HOME_MONTHLY))
			.thenReturn(new SavedPlaceService.SaveResult(1001L, true, SAVED_AT));

		mockMvc.perform(put("/api/v1/users/me/saved-places/1001")
				.with(user("usr_saved").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"source":"HOME_MONTHLY"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("PLACE_SAVED"))
			.andExpect(jsonPath("$.data.placeId").value(1001))
			.andExpect(jsonPath("$.data.saved").value(true))
			.andExpect(jsonPath("$.data.savedAt").value("2026-07-19T03:00:00Z"));
	}

	@Test
	void returnsTheAuthenticatedUsersSavedCards() throws Exception {
		when(service.getSavedPlaces(
			eq("usr_saved"), eq(null), eq(20), eq(PlaceLanguage.EN)))
			.thenReturn(new SavedPlaceService.SavedPlacePage(
				List.of(card()), "opaque-next", true));

		mockMvc.perform(get("/api/v1/users/me/saved-places")
				.with(user("usr_saved").roles("USER"))
				.header("Accept-Language", "en-US"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SAVED_PLACE_LIST_OK"))
			.andExpect(jsonPath("$.data.items", hasSize(1)))
			.andExpect(jsonPath("$.data.items[0].title").value("Saved Place"))
			.andExpect(jsonPath("$.data.items[0].saved").value(true))
			.andExpect(jsonPath("$.data.items[0].savedAt")
				.value("2026-07-19T03:00:00Z"))
			.andExpect(jsonPath("$.data.nextCursor").value("opaque-next"))
			.andExpect(jsonPath("$.data.hasMore").value(true));
	}

	@Test
	void unsavesIdempotently() throws Exception {
		doNothing().when(service).unsave("usr_saved", 1001L);

		mockMvc.perform(delete("/api/v1/users/me/saved-places/1001")
				.with(user("usr_saved").roles("USER")))
			.andExpect(status().isNoContent());
	}

	@Test
	void rejectsMissingOrUnknownSources() throws Exception {
		mockMvc.perform(put("/api/v1/users/me/saved-places/1001")
				.with(user("usr_saved").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(put("/api/v1/users/me/saved-places/1001")
				.with(user("usr_saved").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"source":"UNKNOWN"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void returnsDocumentedErrorsForStaleUsersAndMissingPlaces() throws Exception {
		when(service.getSavedPlaces(any(), any(), any(Integer.class), any()))
			.thenThrow(new SavedPlaceUserUnavailableException());

		mockMvc.perform(get("/api/v1/users/me/saved-places")
				.with(user("usr_missing").roles("USER")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		when(service.save("usr_saved", 404L, SavedPlaceSource.PLACE_DETAIL))
			.thenThrow(new PlaceNotFoundException(404L));

		mockMvc.perform(put("/api/v1/users/me/saved-places/404")
				.with(user("usr_saved").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"source":"PLACE_DETAIL"}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
	}

	private static SavedPlaceService.SavedPlaceCard card() {
		return new SavedPlaceService.SavedPlaceCard(
			1001L,
			"Saved Place",
			ServiceRegionCode.SEOUL,
			"Seoul",
			"Jongno-gu, Seoul",
			null,
			null,
			TravelStyle.CULTURE_EXPERIENCE,
			List.of(),
			"A saved place.",
			true,
			SAVED_AT);
	}
}
