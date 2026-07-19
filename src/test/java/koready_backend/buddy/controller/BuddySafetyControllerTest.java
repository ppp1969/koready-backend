package koready_backend.buddy.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.buddy.application.BuddyBlockService;
import koready_backend.buddy.application.exception.BuddyProfileNotFoundException;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BuddySafetyControllerTest {

	private static final String PATH = "/api/v1/users/me/blocked-profiles/51";
	private static final Instant BLOCKED_AT = Instant.parse("2026-07-19T04:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BuddyBlockService service;

	@Test
	void requiresAuthenticationForBlockAndUnblock() throws Exception {
		mockMvc.perform(put(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(delete(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsTheIdempotentBlockState() throws Exception {
		when(service.block("usr_blocker", 51L))
			.thenReturn(new BuddyBlockService.BlockResult(51L, BLOCKED_AT));

		mockMvc.perform(put(PATH).with(user("usr_blocker").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("BUDDY_PROFILE_BLOCKED"))
			.andExpect(jsonPath("$.data.profileId").value(51))
			.andExpect(jsonPath("$.data.blocked").value(true))
			.andExpect(jsonPath("$.data.blockedAt").value("2026-07-19T04:00:00Z"));
	}

	@Test
	void returnsNoContentWhenUnblocked() throws Exception {
		doNothing().when(service).unblock("usr_blocker", 51L);

		mockMvc.perform(delete(PATH).with(user("usr_blocker").roles("USER")))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
	}

	@Test
	void returnsNotFoundForAnUnavailableTargetProfile() throws Exception {
		when(service.block("usr_blocker", 51L))
			.thenThrow(new BuddyProfileNotFoundException(51L));

		mockMvc.perform(put(PATH).with(user("usr_blocker").roles("USER")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BUDDY_PROFILE_NOT_FOUND"));
	}

	@Test
	void rejectsSelfBlockingAndStalePrincipals() throws Exception {
		when(service.block("usr_self", 51L))
			.thenThrow(new IllegalArgumentException("A user cannot block their own profile"));
		doThrow(new BuddyUserUnavailableException())
			.when(service).unblock("usr_missing", 51L);

		mockMvc.perform(put(PATH).with(user("usr_self").roles("USER")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(delete(PATH).with(user("usr_missing").roles("USER")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsTheStandardErrorEnvelopeForMalformedProfileIds() throws Exception {
		mockMvc.perform(put("/api/v1/users/me/blocked-profiles/not-a-number")
				.with(user("usr_blocker").roles("USER")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
