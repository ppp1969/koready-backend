package koready_backend.home.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
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

import koready_backend.home.application.port.HomeRepository;
import koready_backend.home.application.port.HomeRepository.HomeUser;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcHomeRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	HomeRepository repository;

	@Test
	void resolvesOnlyAnOwnedActiveDefaultLocation() {
		long ownerId = user("usr_home_owner", PlaceLanguage.EN);
		long otherId = user("usr_home_other", PlaceLanguage.KO);
		long ownerLocation = location(ownerId, "Owner campus", ServiceRegionCode.SEOUL);
		long otherLocation = location(otherId, "Other campus", ServiceRegionCode.GANGWON);

		jdbcTemplate.update("UPDATE users SET default_location_id = ? WHERE id = ?",
			ownerLocation, ownerId);
		HomeUser active = repository.findByPublicId("usr_home_owner").orElseThrow();
		assertEquals(PlaceLanguage.EN, active.preferredLanguage());
		assertEquals(ownerLocation, active.currentLocation().locationId());
		assertEquals(ServiceRegionCode.SEOUL, active.currentLocation().serviceRegionCode());

		jdbcTemplate.update("UPDATE users SET default_location_id = ? WHERE id = ?",
			otherLocation, ownerId);
		assertNull(repository.findByPublicId("usr_home_owner").orElseThrow().currentLocation());

		jdbcTemplate.update("UPDATE users SET default_location_id = ? WHERE id = ?",
			ownerLocation, ownerId);
		jdbcTemplate.update("UPDATE user_locations SET deleted_at = ? WHERE id = ?",
			Timestamp.from(Instant.parse("2026-07-19T00:00:00Z")), ownerLocation);
		assertNull(repository.findByPublicId("usr_home_owner").orElseThrow().currentLocation());
	}

	@Test
	void hidesDeletedOrMissingUsersAndAllowsAUserWithoutALocation() {
		long userId = user("usr_home_no_location", PlaceLanguage.KO);

		HomeUser withoutLocation = repository.findByPublicId("usr_home_no_location")
			.orElseThrow();
		assertNull(withoutLocation.currentLocation());
		assertTrue(repository.findByPublicId("usr_home_missing").isEmpty());

		jdbcTemplate.update("UPDATE users SET deleted_at = ? WHERE id = ?",
			Timestamp.from(Instant.parse("2026-07-19T00:00:00Z")), userId);
		assertTrue(repository.findByPublicId("usr_home_no_location").isEmpty());
	}

	private long user(String publicId, PlaceLanguage language) {
		jdbcTemplate.update(
			"""
			INSERT INTO users (public_id, preferred_language, signup_status)
			VALUES (?, ?, 'COMPLETED')
			""",
			publicId,
			language.name());
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private long location(long userId, String displayName, ServiceRegionCode region) {
		jdbcTemplate.update(
			"""
			INSERT INTO user_locations (user_id, display_name, service_region_code)
			VALUES (?, ?, ?)
			""",
			userId,
			displayName,
			region.name());
		return jdbcTemplate.queryForObject(
			"SELECT id FROM user_locations WHERE user_id = ? AND display_name = ?",
			Long.class,
			userId,
			displayName);
	}
}
