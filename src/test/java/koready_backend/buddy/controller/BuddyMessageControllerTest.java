package koready_backend.buddy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

import koready_backend.buddy.application.BuddyMessageService;
import koready_backend.buddy.application.BuddyMessageQueryService;
import koready_backend.buddy.application.exception.BuddyProfileRequiredException;
import koready_backend.buddy.application.exception.InvalidMessageCursorException;
import koready_backend.buddy.application.exception.MessageIdempotencyConflictException;
import koready_backend.buddy.application.exception.MessageNotAllowedException;
import koready_backend.buddy.application.exception.MessageThreadNotFoundException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BuddyMessageControllerTest {

	private static final String KEY = "message-key-001";
	private static final Instant SENT_AT = Instant.parse("2026-07-19T08:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BuddyMessageService service;

	@MockitoBean
	private BuddyMessageQueryService queryService;

	@Test
	void requiresAuthenticationForEveryMessageOperation() throws Exception {
		mockMvc.perform(get("/api/v1/message-threads"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(get("/api/v1/message-threads/thread_001"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(put("/api/v1/message-threads/thread_001/read"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(post("/api/v1/message-threads")
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"receiverProfileId":51,"placeId":1001,"content":"Hello"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(post("/api/v1/message-threads/thread_001/messages")
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"Reply\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsThreadListDetailAndExplicitReadResults() throws Exception {
		BuddyMessageQueryService.ThreadSummary summary =
			new BuddyMessageQueryService.ThreadSummary(
				"thread_001",
				new BuddyMessageService.PlaceSummary(
					1001L, "Gimbap Festival", "https://example.com/place.jpg"),
				new BuddyMessageService.ProfileSummary(
					51L, "Receiver", "https://example.com/profile.jpg"),
				"Hello",
				SENT_AT,
				2L,
				true,
				false);
		when(queryService.getThreads("usr_sender", null, 20))
			.thenReturn(new BuddyMessageQueryService.ThreadListResult(
				List.of(summary), "next_threads", true, 2L));
		when(queryService.getThread("usr_sender", "thread_001", null, 20))
			.thenReturn(new BuddyMessageService.ThreadResult(
				"thread_001",
				summary.place(),
				summary.otherProfile(),
				List.of(message(9001L, "Hello")),
				null,
				false,
				false));
		when(queryService.markRead("usr_sender", "thread_001"))
			.thenReturn(new BuddyMessageQueryService.ReadResult(
				"thread_001", SENT_AT, 0L, 0L));

		mockMvc.perform(get("/api/v1/message-threads")
				.with(user("usr_sender").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("MESSAGE_THREADS_OK"))
			.andExpect(jsonPath("$.data.items[0].threadId").value("thread_001"))
			.andExpect(jsonPath("$.data.items[0].blocked").value(true))
			.andExpect(jsonPath("$.data.items[0].canReply").value(false))
			.andExpect(jsonPath("$.data.nextCursor").value("next_threads"))
			.andExpect(jsonPath("$.data.unreadTotal").value(2));

		mockMvc.perform(get("/api/v1/message-threads/thread_001")
				.with(user("usr_sender").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("MESSAGE_THREAD_OK"))
			.andExpect(jsonPath("$.data.messages[0].messageId").value(9001))
			.andExpect(jsonPath("$.data.canReply").value(false));

		mockMvc.perform(put("/api/v1/message-threads/thread_001/read")
				.with(user("usr_sender").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("MESSAGE_THREAD_READ"))
			.andExpect(jsonPath("$.data.threadUnreadCount").value(0))
			.andExpect(jsonPath("$.data.unreadTotal").value(0));
	}

	@Test
	void validatesMessagePagingAndMapsCursorErrors() throws Exception {
		mockMvc.perform(get("/api/v1/message-threads?size=51")
				.with(user("usr_sender").roles("USER")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		when(queryService.getThreads("usr_sender", "bad-cursor", 20))
			.thenThrow(new InvalidMessageCursorException());
		mockMvc.perform(get("/api/v1/message-threads?cursor=bad-cursor")
				.with(user("usr_sender").roles("USER")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
	}

	@Test
	void hidesForeignThreadsAndMapsMissingBuddyProfile() throws Exception {
		when(queryService.getThread("usr_sender", "thread_hidden", null, 20))
			.thenThrow(new MessageThreadNotFoundException());
		when(queryService.markRead("usr_sender", "thread_hidden"))
			.thenThrow(new MessageThreadNotFoundException());
		when(queryService.getThreads("usr_no_profile", null, 20))
			.thenThrow(new BuddyProfileRequiredException());

		mockMvc.perform(get("/api/v1/message-threads/thread_hidden")
				.with(user("usr_sender").roles("USER")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MESSAGE_THREAD_NOT_FOUND"));
		mockMvc.perform(put("/api/v1/message-threads/thread_hidden/read")
				.with(user("usr_sender").roles("USER")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MESSAGE_THREAD_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/message-threads")
				.with(user("usr_no_profile").roles("USER")))
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("BUDDY_PROFILE_REQUIRED"));
	}

	@Test
	void createsAThreadWithTheStandardEnvelope() throws Exception {
		BuddyMessageService.MessageResult message = message(9001L, "Hello");
		when(service.createThread(
			"usr_sender",
			KEY,
			new BuddyMessageService.CreateThreadCommand(51L, 1001L, "Hello")))
			.thenReturn(new BuddyMessageService.ThreadResult(
				"thread_001",
				new BuddyMessageService.PlaceSummary(
					1001L, "Gimbap Festival", "https://example.com/place.jpg"),
				new BuddyMessageService.ProfileSummary(
					51L, "Receiver", "https://example.com/profile.jpg"),
				List.of(message),
				null,
				false,
				true));

		mockMvc.perform(post("/api/v1/message-threads")
				.with(user("usr_sender").roles("USER"))
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"receiverProfileId":51,"placeId":1001,"content":"Hello"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("MESSAGE_THREAD_CREATED"))
			.andExpect(jsonPath("$.data.threadId").value("thread_001"))
			.andExpect(jsonPath("$.data.place.placeId").value(1001))
			.andExpect(jsonPath("$.data.otherProfile.profileId").value(51))
			.andExpect(jsonPath("$.data.messages[0].messageId").value(9001))
			.andExpect(jsonPath("$.data.messages[0].read").value(false))
			.andExpect(jsonPath("$.data.hasMore").value(false))
			.andExpect(jsonPath("$.data.canReply").value(true));
	}

	@Test
	void repliesWithTheCreatedMessage() throws Exception {
		when(service.reply("usr_sender", "thread_001", KEY, "Reply"))
			.thenReturn(message(9002L, "Reply"));

		mockMvc.perform(post("/api/v1/message-threads/thread_001/messages")
				.with(user("usr_sender").roles("USER"))
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"Reply\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("MESSAGE_CREATED"))
			.andExpect(jsonPath("$.data.messageId").value(9002))
			.andExpect(jsonPath("$.data.threadId").value("thread_001"))
			.andExpect(jsonPath("$.data.content").value("Reply"));
	}

	@Test
	void rejectsMissingHeadersAndMalformedBodies() throws Exception {
		mockMvc.perform(post("/api/v1/message-threads")
				.with(user("usr_sender").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"receiverProfileId":51,"placeId":1001,"content":"Hello"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(post("/api/v1/message-threads")
				.with(user("usr_sender").roles("USER"))
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"receiverProfileId\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void mapsMessagePolicyAndIdempotencyErrors() throws Exception {
		BuddyMessageService.CreateThreadCommand command =
			new BuddyMessageService.CreateThreadCommand(51L, 1001L, "Hello");
		when(service.createThread("usr_blocked", KEY, command))
			.thenThrow(new MessageNotAllowedException());
		when(service.createThread("usr_no_profile", KEY, command))
			.thenThrow(new BuddyProfileRequiredException());
		when(service.createThread("usr_conflict", KEY, command))
			.thenThrow(new MessageIdempotencyConflictException());

		performCreate("usr_blocked")
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("MESSAGE_NOT_ALLOWED"));
		performCreate("usr_no_profile")
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("BUDDY_PROFILE_REQUIRED"));
		performCreate("usr_conflict")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
	}

	@Test
	void hidesUnknownOrForeignThreadsWithNotFound() throws Exception {
		when(service.reply("usr_sender", "thread_hidden", KEY, "Reply"))
			.thenThrow(new MessageThreadNotFoundException());

		mockMvc.perform(post("/api/v1/message-threads/thread_hidden/messages")
				.with(user("usr_sender").roles("USER"))
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"Reply\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MESSAGE_THREAD_NOT_FOUND"));
	}

	private org.springframework.test.web.servlet.ResultActions performCreate(String userId)
		throws Exception {
		return mockMvc.perform(post("/api/v1/message-threads")
			.with(user(userId).roles("USER"))
			.header("Idempotency-Key", KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"receiverProfileId":51,"placeId":1001,"content":"Hello"}
				"""));
	}

	private static BuddyMessageService.MessageResult message(
		long messageId,
		String content
	) {
		return new BuddyMessageService.MessageResult(
			messageId,
			"thread_001",
			50L,
			51L,
			1001L,
			content,
			SENT_AT,
			false,
			null);
	}
}
