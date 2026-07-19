package koready_backend.location.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.location.application.UserLocationService;
import koready_backend.location.application.exception.ExpiredLocationSearchTokenException;
import koready_backend.location.application.exception.InvalidLocationSearchTokenException;
import koready_backend.location.application.exception.UserLocationNotFoundException;
import koready_backend.place.domain.ServiceRegionCode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserLocationControllerTest {

	private static final String PATH = "/api/v1/users/me/locations";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserLocationService service;

	@Test
	void requiresAuthenticationForEveryOperation() throws Exception {
		mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCreateBody()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(put(PATH + "/1/default"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(PATH + "/1"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsTheUsersActiveLocations() throws Exception {
		when(service.getAll("usr_emma")).thenReturn(
			new UserLocationService.LocationList(List.of(location(true))));

		mockMvc.perform(get(PATH).with(user("usr_emma").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("USER_LOCATION_LIST_OK"))
			.andExpect(jsonPath("$.data.items[0].locationId").value(101))
			.andExpect(jsonPath("$.data.items[0].displayName").value("서울시청"))
			.andExpect(jsonPath("$.data.items[0].customLabel").value("학교"))
			.andExpect(jsonPath("$.data.items[0].serviceRegionCode").value("SEOUL"))
			.andExpect(jsonPath("$.data.items[0].default").value(true));
	}

	@Test
	void createsALocationWithA201Envelope() throws Exception {
		when(service.create(
			"usr_emma",
			new UserLocationService.CreateCommand("locsrch_valid", "학교", true)))
			.thenReturn(location(true));

		mockMvc.perform(post(PATH)
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCreateBody()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("USER_LOCATION_CREATED"))
			.andExpect(jsonPath("$.data.locationId").value(101))
			.andExpect(jsonPath("$.data.default").value(true));
	}

	@Test
	void validatesTheCreateBody() throws Exception {
		for (String body : List.of(
			"{\"searchResultToken\":\"\",\"setDefault\":true}",
			"{\"searchResultToken\":\"locsrch_valid\",\"customLabel\":\""
				+ "x".repeat(31) + "\",\"setDefault\":true}",
			"{\"searchResultToken\":\"locsrch_valid\"}")) {
			mockMvc.perform(post(PATH)
					.with(user("usr_emma").roles("USER"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
	}

	@Test
	void mapsExpiredAndInvalidSearchTokens() throws Exception {
		when(service.create(
			org.mockito.ArgumentMatchers.eq("usr_emma"),
			any(UserLocationService.CreateCommand.class)))
			.thenThrow(new ExpiredLocationSearchTokenException())
			.thenThrow(new InvalidLocationSearchTokenException());

		mockMvc.perform(post(PATH)
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCreateBody()))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("LOCATION_SEARCH_RESULT_EXPIRED"));

		mockMvc.perform(post(PATH)
				.with(user("usr_emma").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCreateBody()))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("LOCATION_SEARCH_RESULT_INVALID"));
	}

	@Test
	void changesTheDefaultAndDeletesAnOwnedLocation() throws Exception {
		when(service.setDefault("usr_emma", 101L)).thenReturn(location(true));

		mockMvc.perform(put(PATH + "/101/default")
				.with(user("usr_emma").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("USER_LOCATION_DEFAULT_UPDATED"))
			.andExpect(jsonPath("$.data.default").value(true));

		mockMvc.perform(delete(PATH + "/101")
				.with(user("usr_emma").roles("USER")))
			.andExpect(status().isNoContent());
	}

	@Test
	void hidesLocationsThatAreMissingDeletedOrOwnedBySomeoneElse() throws Exception {
		doThrow(new UserLocationNotFoundException(999L))
			.when(service).delete("usr_emma", 999L);

		mockMvc.perform(delete(PATH + "/999")
				.with(user("usr_emma").roles("USER")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("USER_LOCATION_NOT_FOUND"));
	}

	private static String validCreateBody() {
		return """
			{
			  "searchResultToken": "locsrch_valid",
			  "customLabel": "학교",
			  "setDefault": true
			}
			""";
	}

	private static UserLocationService.Location location(boolean isDefault) {
		return new UserLocationService.Location(
			101L,
			"서울시청",
			"학교",
			"서울특별시 중구 세종대로 110",
			"서울특별시 중구 태평로1가 31",
			37.5666,
			126.9784,
			ServiceRegionCode.SEOUL,
			isDefault,
			Instant.parse("2026-07-19T07:00:00Z"));
	}
}
