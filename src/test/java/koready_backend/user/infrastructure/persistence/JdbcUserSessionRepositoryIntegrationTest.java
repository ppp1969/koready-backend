package koready_backend.user.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.user.application.port.UserSessionRepository;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcUserSessionRepositoryIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserSessionRepository repository;

	@Test
	void aggregatesTheCurrentProfileMessageAndTermsState() {
		long viewerUserId = user("usr_session_viewer", "EN", "COMPLETED");
		identity(viewerUserId, "viewer@example.com");
		long viewerProfileId = profile(viewerUserId, "Viewer");
		long senderUserId = user("usr_session_sender", "KO", "COMPLETED");
		identity(senderUserId, "sender@example.com");
		long senderProfileId = profile(senderUserId, "Sender");
		long placeId = place();
		long threadId = thread(placeId, viewerProfileId, senderProfileId);
		message(threadId, senderProfileId, viewerProfileId, null, null);
		message(threadId, senderProfileId, viewerProfileId, NOW, null);
		message(threadId, senderProfileId, viewerProfileId, null, NOW);
		requiredTerm();

		var result = repository.find("usr_session_viewer", NOW).orElseThrow();

		assertEquals("viewer@example.com", result.email());
		assertTrue(result.buddyProfileExists());
		assertEquals(1L, result.unreadMessageCount());
		assertTrue(result.termsNeedReAgreement());
	}

	@Test
	void excludesDeletedUsers() {
		long userId = user("usr_session_deleted", "KO", "NEED_TERMS");
		identity(userId, "deleted@example.com");
		jdbcTemplate.update("UPDATE users SET deleted_at = NOW(6) WHERE id = ?", userId);

		assertTrue(repository.find("usr_session_deleted", NOW).isEmpty());
		assertTrue(repository.find("usr_session_missing", NOW).isEmpty());
	}

	@Test
	void reportsNoReAgreementAfterAgreeingToTheCurrentRequiredVersion() {
		long userId = user("usr_session_agreed", "KO", "COMPLETED");
		identity(userId, "agreed@example.com");
		long versionId = requiredTerm();
		jdbcTemplate.update(
			"""
			INSERT INTO user_term_agreements
			    (user_id, term_version_id, agreed, agreed_at, created_at, updated_at)
			VALUES (?, ?, TRUE, ?, ?, ?)
			""",
			userId, versionId, NOW, NOW, NOW);

		assertFalse(repository.find("usr_session_agreed", NOW)
			.orElseThrow().termsNeedReAgreement());
	}

	private long user(String publicId, String language, String status) {
		jdbcTemplate.update(
			"INSERT INTO users (public_id, preferred_language, signup_status) VALUES (?, ?, ?)",
			publicId, language, status);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private void identity(long userId, String email) {
		jdbcTemplate.update(
			"""
			INSERT INTO user_social_identities
			    (user_id, provider, provider_subject, email, email_verified,
			     last_login_at, created_at, updated_at)
			VALUES (?, 'GOOGLE', ?, ?, TRUE, ?, ?, ?)
			""",
			userId, "subject-" + userId, email, NOW, NOW, NOW);
	}

	private long profile(long userId, String nickname) {
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_profiles
			    (user_id, nickname, nationality_code, korean_level,
			     profile_public, sns_public, allows_messages, created_at, updated_at)
			VALUES (?, ?, 'US', 'BEGINNER', TRUE, FALSE, TRUE, ?, ?)
			""",
			userId, nickname, NOW, NOW);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM buddy_profiles WHERE user_id = ?", Long.class, userId);
	}

	private long place() {
		jdbcTemplate.update(
			"INSERT INTO places (kto_content_id, active, show_flag) VALUES ('session-place', TRUE, TRUE)");
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = 'session-place'", Long.class);
	}

	private long thread(long placeId, long firstProfileId, long secondProfileId) {
		long low = Math.min(firstProfileId, secondProfileId);
		long high = Math.max(firstProfileId, secondProfileId);
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_message_threads
			    (public_id, place_id, profile_low_id, profile_high_id, created_at, updated_at)
			VALUES ('thread-session', ?, ?, ?, ?, ?)
			""",
			placeId, low, high, NOW, NOW);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM buddy_message_threads WHERE public_id = 'thread-session'",
			Long.class);
	}

	private void message(
		long threadId,
		long senderProfileId,
		long receiverProfileId,
		Instant readAt,
		Instant deletedAt
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_messages
			    (thread_id, sender_profile_id, receiver_profile_id, content,
			     sent_at, read_at, deleted_by_receiver_at)
			VALUES (?, ?, ?, 'hello', ?, ?, ?)
			""",
			threadId, senderProfileId, receiverProfileId, NOW, readAt, deletedAt);
	}

	private long requiredTerm() {
		String code = "SESSION_TERM_" + System.nanoTime();
		jdbcTemplate.update(
			"INSERT INTO term_definitions (code, display_order, enabled) VALUES (?, 1, TRUE)",
			code);
		long termId = jdbcTemplate.queryForObject(
			"SELECT id FROM term_definitions WHERE code = ?", Long.class, code);
		jdbcTemplate.update(
			"""
			INSERT INTO term_versions
			    (term_id, version_label, title, content_url, required,
			     effective_at, published_at)
			VALUES (?, '1.0', 'Session terms', 'https://koready.cloud/terms', TRUE,
			        '2026-08-01 00:00:00', '2026-08-01 00:00:00')
			""",
			termId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM term_versions WHERE term_id = ?", Long.class, termId);
	}
}
