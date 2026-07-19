package koready_backend.buddy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

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
import koready_backend.buddy.application.port.BuddyMessageRepository.StoredMessage;

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
