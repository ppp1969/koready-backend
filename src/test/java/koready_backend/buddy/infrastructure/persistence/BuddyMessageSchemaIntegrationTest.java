package koready_backend.buddy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.buddy.application.port.BuddyMessageRepository;
import koready_backend.buddy.application.port.BuddyMessageRepository.IdempotencyStatus;
import koready_backend.buddy.application.port.BuddyMessageRepository.MessageThread;
import koready_backend.buddy.application.port.BuddyMessageRepository.MessagePageCriteria;
import koready_backend.buddy.application.port.BuddyMessageRepository.StoredMessage;
import koready_backend.buddy.application.port.BuddyMessageRepository.ThreadListCriteria;
import koready_backend.buddy.application.port.BuddyMessageRepository.ThreadListCursor;
import koready_backend.buddy.application.port.BuddyMessageRepository.ThreadSummaryRow;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class BuddyMessageSchemaIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-07-19T08:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BuddyMessageRepository repository;

	@Test
	void flywayCreatesTheMessageSendFoundation() {
		Integer tableCount = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM information_schema.tables
			WHERE table_schema = DATABASE()
			  AND table_name IN (
			      'buddy_message_threads',
			      'buddy_messages',
			      'message_idempotency_keys'
			  )
			""",
			Integer.class);

		assertEquals(3, tableCount);
	}

	@Test
	void storesOneThreadPerPlaceAndParticipantPairWithIdempotentMessages() {
		long senderUserId = user("usr_message_sender", "EN");
		long receiverUserId = user("usr_message_receiver", "KO");
		long senderProfileId = profile(senderUserId, "Sender", true, true);
		long receiverProfileId = profile(receiverUserId, "Receiver", true, true);
		long placeId = place("Message Festival");

		assertEquals(senderUserId,
			repository.findActiveUser("usr_message_sender").orElseThrow().userId());
		assertEquals(senderProfileId,
			repository.findProfileByUserId(senderUserId).orElseThrow().profileId());
		assertEquals(receiverProfileId,
			repository.findActiveProfile(receiverProfileId).orElseThrow().profileId());
		assertEquals("Message Festival",
			repository.findActivePlace(placeId, "EN").orElseThrow().title());

		assertEquals(IdempotencyStatus.CLAIMED,
			repository.claimIdempotency(
				senderProfileId, "CaseKey-001", "a".repeat(64), NOW).status());
		MessageThread thread = repository.findOrCreateThread(
			placeId,
			senderProfileId,
			receiverProfileId,
			"thread_database_001",
			NOW);
		MessageThread repeated = repository.findOrCreateThread(
			placeId,
			receiverProfileId,
			senderProfileId,
			"thread_database_unused",
			NOW.plusSeconds(1));
		assertEquals(thread, repeated);

		StoredMessage message = repository.appendMessage(
			thread,
			senderProfileId,
			receiverProfileId,
			"Hello from MySQL",
			NOW);
		repository.completeIdempotency(
			senderProfileId, "CaseKey-001", message.messageId());

		BuddyMessageRepository.IdempotencyClaim replay = repository.claimIdempotency(
			senderProfileId, "CaseKey-001", "a".repeat(64), NOW.plusSeconds(2));
		assertEquals(IdempotencyStatus.REPLAY, replay.status());
		assertEquals(message.messageId(), replay.existingMessageId());
		assertEquals(IdempotencyStatus.CONFLICT,
			repository.claimIdempotency(
				senderProfileId, "CaseKey-001", "b".repeat(64), NOW).status());
		assertEquals(IdempotencyStatus.CLAIMED,
			repository.claimIdempotency(
				senderProfileId, "casekey-001", "c".repeat(64), NOW).status());

		assertEquals(message, repository.findMessage(message.messageId()).orElseThrow());
		assertTrue(repository.findParticipantThread(
			"thread_database_001", senderProfileId).isPresent());
		assertTrue(repository.findParticipantThread(
			"thread_database_001", receiverProfileId).isPresent());
		assertTrue(repository.findParticipantThread(
			"thread_database_001", Long.MAX_VALUE).isEmpty());
		assertEquals(1, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM buddy_message_threads WHERE place_id = ?",
			Integer.class,
			placeId));
		assertEquals(1, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM buddy_messages WHERE thread_id = ?",
			Integer.class,
			thread.databaseId()));
	}

	@Test
	void databaseRejectsSelfThreadsAndInvalidMessageContent() {
		long senderUserId = user("usr_message_constraint", "KO");
		long receiverUserId = user("usr_message_constraint_receiver", "KO");
		long senderProfileId = profile(senderUserId, "Sender", true, true);
		long receiverProfileId = profile(receiverUserId, "Receiver", true, true);
		long placeId = place("Constraint Festival");

		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
			"""
			INSERT INTO buddy_message_threads
			    (public_id, place_id, profile_low_id, profile_high_id,
			     created_at, updated_at)
			VALUES ('thread_self', ?, ?, ?, NOW(6), NOW(6))
			""",
			placeId,
			senderProfileId,
			senderProfileId));

		MessageThread thread = repository.findOrCreateThread(
			placeId,
			senderProfileId,
			receiverProfileId,
			"thread_constraint_001",
			NOW);
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
			"""
			INSERT INTO buddy_messages
			    (thread_id, sender_profile_id, receiver_profile_id, content, sent_at)
			VALUES (?, ?, ?, '   ', NOW(6))
			""",
			thread.databaseId(),
			senderProfileId,
			receiverProfileId));
	}

	@Test
	void queriesInboxWithStableCursorsBlockingAndExplicitRead() {
		long viewerUserId = user("usr_inbox_viewer", "EN");
		long blockedUserId = user("usr_inbox_blocked", "KO");
		long activeUserId = user("usr_inbox_active", "KO");
		long deletedUserId = user("usr_inbox_deleted", "KO");
		long viewerProfileId = profile(viewerUserId, "Viewer", true, true);
		long blockedProfileId = profile(blockedUserId, "Blocked", false, false);
		long activeProfileId = profile(activeUserId, "Active", true, true);
		long deletedProfileId = profile(deletedUserId, "Deleted", true, true);
		long placeId = place("Inbox Festival");

		MessageThread newestThread = repository.findOrCreateThread(
			placeId, viewerProfileId, blockedProfileId, "thread_inbox_newest", NOW);
		StoredMessage incoming = repository.appendMessage(
			newestThread,
			blockedProfileId,
			viewerProfileId,
			"  Hello\nfrom the blocked profile  ",
			NOW.plusSeconds(10));
		repository.appendMessage(
			newestThread,
			viewerProfileId,
			blockedProfileId,
			"Reply",
			NOW.plusSeconds(20));

		MessageThread olderThread = repository.findOrCreateThread(
			placeId, viewerProfileId, activeProfileId, "thread_inbox_older", NOW);
		repository.appendMessage(
			olderThread,
			activeProfileId,
			viewerProfileId,
			"Older incoming message",
			NOW.plusSeconds(5));

		MessageThread deletedThread = repository.findOrCreateThread(
			placeId, viewerProfileId, deletedProfileId, "thread_inbox_deleted", NOW);
		repository.appendMessage(
			deletedThread,
			deletedProfileId,
			viewerProfileId,
			"This thread must disappear",
			NOW.plusSeconds(30));
		jdbcTemplate.update(
			"UPDATE users SET deleted_at = NOW(6) WHERE id = ?", deletedUserId);
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_blocks (blocker_user_id, blocked_user_id, created_at)
			VALUES (?, ?, NOW(6))
			""",
			blockedUserId,
			viewerUserId);

		List<ThreadSummaryRow> firstPage = repository.findThreads(
			new ThreadListCriteria(viewerProfileId, "EN", null, 1));
		assertEquals(1, firstPage.size());
		assertEquals("thread_inbox_newest", firstPage.getFirst().threadPublicId());
		assertEquals("Inbox Festival", firstPage.getFirst().place().title());
		assertEquals(1, firstPage.getFirst().unreadCount());
		assertTrue(firstPage.getFirst().blocked());
		assertFalse(firstPage.getFirst().otherProfile().profilePublic());

		ThreadSummaryRow boundary = firstPage.getFirst();
		List<ThreadSummaryRow> secondPage = repository.findThreads(
			new ThreadListCriteria(
				viewerProfileId,
				"EN",
				new ThreadListCursor(
					boundary.updatedAt(), boundary.threadDatabaseId()),
				10));
		assertEquals(1, secondPage.size());
		assertEquals("thread_inbox_older", secondPage.getFirst().threadPublicId());
		assertEquals(2, repository.countUnreadMessages(viewerProfileId));

		assertTrue(repository.findThreadContext(
			"thread_inbox_newest", viewerProfileId, "EN").orElseThrow().blocked());
		assertTrue(repository.findThreadContext(
			"thread_inbox_deleted", viewerProfileId, "EN").isEmpty());

		List<StoredMessage> messages = repository.findMessages(
			new MessagePageCriteria(
				newestThread.databaseId(),
				newestThread.publicId(),
				viewerProfileId,
				null,
				10));
		assertEquals(2, messages.size());
		assertTrue(messages.getFirst().messageId() > messages.getLast().messageId());
		List<StoredMessage> olderMessages = repository.findMessages(
			new MessagePageCriteria(
				newestThread.databaseId(),
				newestThread.publicId(),
				viewerProfileId,
				messages.getFirst().messageId(),
				10));
		assertEquals(1, olderMessages.size());
		assertEquals(incoming.messageId(), olderMessages.getFirst().messageId());
		assertEquals(2, repository.countUnreadMessages(viewerProfileId));

		Instant readAt = NOW.plusSeconds(40);
		assertEquals(readAt, repository.markRead(newestThread, viewerProfileId, readAt));
		assertEquals(readAt,
			repository.findMessage(incoming.messageId()).orElseThrow().readAt());
		assertEquals(1, repository.countUnreadMessages(viewerProfileId));
		assertEquals(readAt,
			repository.markRead(
				newestThread, viewerProfileId, NOW.plusSeconds(50)));
	}

	private long user(String publicId, String language) {
		jdbcTemplate.update(
			"""
			INSERT INTO users (public_id, preferred_language, signup_status)
			VALUES (?, ?, 'COMPLETED')
			""",
			publicId,
			language);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private long profile(
		long userId,
		String nickname,
		boolean profilePublic,
		boolean allowsMessages
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_profiles
			    (user_id, nickname, korean_level, profile_public, sns_public,
			     allows_messages, created_at, updated_at)
			VALUES (?, ?, 'BEGINNER', ?, FALSE, ?, NOW(6), NOW(6))
			""",
			userId,
			nickname,
			profilePublic,
			allowsMessages);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM buddy_profiles WHERE user_id = ?", Long.class, userId);
	}

	private long place(String title) {
		String contentId = "message-" + title.replace(" ", "-").toLowerCase();
		jdbcTemplate.update(
			"""
			INSERT INTO places (kto_content_id, active, show_flag)
			VALUES (?, TRUE, TRUE)
			""",
			contentId);
		long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?", Long.class, contentId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, translation_source)
			VALUES (?, 'KO', ?, 'MANUAL_EDITED')
			""",
			placeId,
			title);
		return placeId;
	}
}
