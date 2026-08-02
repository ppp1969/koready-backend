package koready_backend.auth.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import koready_backend.auth.application.port.AuthRepository;
import koready_backend.auth.domain.GoogleIdentity;
import koready_backend.auth.domain.UserRole;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcAuthRepositoryIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-08-01T03:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private AuthRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void createsUsersByGoogleSubjectWithoutMergingTheSameEmail() {
		var first = repository.createGoogleUser(
			new GoogleIdentity("google-subject-1", "shared@example.com"),
			"usr_google_1",
			NOW);
		var second = repository.createGoogleUser(
			new GoogleIdentity("google-subject-2", "shared@example.com"),
			"usr_google_2",
			NOW.plusSeconds(1));

		assertNotNull(first);
		assertNotNull(second);
		assertEquals("usr_google_1",
			repository.findByGoogleSubject("google-subject-1")
				.orElseThrow()
				.publicId());
		assertEquals("usr_google_2",
			repository.findByGoogleSubject("google-subject-2")
				.orElseThrow()
				.publicId());
		assertEquals(2, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM users WHERE public_id LIKE 'usr_google_%'",
			Integer.class));
	}

	@Test
	void defaultsNewUsersToUserAndRejectsUnknownRoles() {
		var user = repository.createGoogleUser(
			new GoogleIdentity("google-subject-role", "role@example.com"),
			"usr_google_role",
			NOW);

		assertEquals("USER", jdbcTemplate.queryForObject(
			"SELECT role FROM users WHERE id = ?",
			String.class,
			user.id()));
		assertEquals(UserRole.USER, user.role());
		jdbcTemplate.update(
			"UPDATE users SET role = 'ADMIN' WHERE id = ?",
			user.id());
		assertEquals(
			UserRole.ADMIN,
			repository.findActiveUser(user.id()).orElseThrow().role());
		var exception = assertThrows(
			DataAccessException.class,
			() -> jdbcTemplate.update(
				"UPDATE users SET role = 'OWNER' WHERE id = ?",
				user.id()));
		assertTrue(exception.getMostSpecificCause()
			.getMessage()
			.contains("chk_users_role"));
	}

	@Test
	void persistsOnlyRefreshHashesAndRevokesTheLockedSession() {
		var user = repository.createGoogleUser(
			new GoogleIdentity("google-subject-session", "session@example.com"),
			"usr_google_session",
			NOW);
		repository.saveRefreshSession(
			user.id(),
			"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
			"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
			NOW,
			NOW.plusSeconds(3600));

		var session = repository.findRefreshSessionForUpdate(
			"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
			.orElseThrow();
		repository.revokeRefreshSession(session.id(), NOW.plusSeconds(10));

		var revoked = repository.findRefreshSessionForUpdate(session.tokenHash())
			.orElseThrow();
		assertEquals(NOW.plusSeconds(10), revoked.revokedAt());
		assertTrue(jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM auth_refresh_sessions
			WHERE token_hash = ?
			  AND token_hash NOT LIKE 'rft_%'
			""",
			Integer.class,
			session.tokenHash()) == 1);
	}

	@Test
	void keepsOnlyOneActiveRefreshSessionForTheSameUserAndDevice() {
		var user = repository.createGoogleUser(
			new GoogleIdentity("google-subject-device", "device@example.com"),
			"usr_google_device",
			NOW);
		String deviceHash =
			"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
		repository.saveRefreshSession(
			user.id(),
			"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
			deviceHash,
			NOW,
			NOW.plusSeconds(3600));

		repository.revokeActiveRefreshSessions(
			user.id(), deviceHash, NOW.plusSeconds(10));

		assertEquals(0, jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM auth_refresh_sessions
			WHERE user_id = ?
			  AND device_id_hash = ?
			  AND revoked_at IS NULL
			""",
			Integer.class,
			user.id(),
			deviceHash));
	}
}
