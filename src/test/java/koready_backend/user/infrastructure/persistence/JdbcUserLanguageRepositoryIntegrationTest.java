package koready_backend.user.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.port.UserLanguageRepository;
import koready_backend.user.application.port.UserLanguageRepository.UserLanguageState;
import koready_backend.user.domain.SignupStatus;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class JdbcUserLanguageRepositoryIntegrationTest {

	private static final Instant UPDATED_AT = Instant.parse("2026-07-19T03:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserLanguageRepository repository;

	@Test
	void locksAndUpdatesTheActiveUsersLanguageAndSignupStatus() {
		long userId = user("usr_language_db", "KO", "NEED_LANGUAGE");

		UserLanguageState before = repository
			.findByPublicIdForUpdate("usr_language_db")
			.orElseThrow();
		UserLanguageState after = repository.update(
			userId,
			PlaceLanguage.EN,
			SignupStatus.NEED_ONBOARDING,
			UPDATED_AT);

		assertEquals(PlaceLanguage.KO, before.language());
		assertEquals(SignupStatus.NEED_LANGUAGE, before.signupStatus());
		assertEquals(PlaceLanguage.EN, after.language());
		assertEquals(SignupStatus.NEED_ONBOARDING, after.signupStatus());
		assertEquals(UPDATED_AT, after.updatedAt());
	}

	@Test
	void excludesDeletedAndMissingUsers() {
		long deleted = user("usr_language_deleted", "KO", "COMPLETED");
		jdbcTemplate.update("UPDATE users SET deleted_at = NOW(6) WHERE id = ?", deleted);

		assertTrue(repository.findByPublicIdForUpdate("usr_language_deleted").isEmpty());
		assertTrue(repository.findByPublicIdForUpdate("usr_language_missing").isEmpty());
	}

	private long user(String publicId, String language, String status) {
		jdbcTemplate.update(
			"""
			INSERT INTO users (public_id, preferred_language, signup_status)
			VALUES (?, ?, ?)
			""",
			publicId,
			language,
			status);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}
}
