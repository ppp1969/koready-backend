package koready_backend.buddy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

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

import koready_backend.buddy.application.port.BuddyProfileRepository;
import koready_backend.buddy.application.port.BuddyProfileRepository.BuddyProfileRecord;
import koready_backend.buddy.domain.BuddyProfileDraft;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.domain.PlaceLanguage;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcBuddyProfileRepositoryIntegrationTest {

	private static final Instant FIRST = Instant.parse("2026-07-19T03:00:00Z");
	private static final Instant SECOND = Instant.parse("2026-07-19T04:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BuddyProfileRepository repository;

	@Test
	void createsAndFullyReplacesOneProfilePerUser() {
		long userId = user("usr_buddy_db");

		BuddyProfileRecord created = repository.save(userId, firstDraft(), FIRST);
		BuddyProfileRecord updated = repository.save(userId, secondDraft(), SECOND);

		assertEquals(created.profileId(), updated.profileId());
		assertEquals(FIRST, updated.createdAt());
		assertEquals(SECOND, updated.updatedAt());
		assertEquals("Emma Updated", updated.profile().nickname());
		assertEquals(List.of(PlaceLanguage.KO), updated.profile().availableLanguages());
		assertEquals(List.of(BuddyStyle.QUIET_TRAVEL), updated.profile().buddyStyles());
		assertEquals(
			List.of(new BuddySocialLink(SocialLinkType.THREADS, "@emma_new")),
			updated.profile().socialLinks());
		assertEquals(1, count("buddy_profiles", "user_id", userId));
		assertEquals(1, count("buddy_profile_languages", "profile_id", updated.profileId()));
		assertEquals(1, count("buddy_profile_styles", "profile_id", updated.profileId()));
		assertEquals(1, count("buddy_social_links", "profile_id", updated.profileId()));
	}

	@Test
	void resolvesOnlyActiveUsersAndReadsAssociationOrder() {
		long active = user("usr_buddy_active");
		long deleted = user("usr_buddy_deleted");
		jdbcTemplate.update("UPDATE users SET deleted_at = NOW(6) WHERE id = ?", deleted);
		repository.save(active, firstDraft(), FIRST);

		assertEquals(active, repository.findActiveUserId("usr_buddy_active").orElseThrow());
		assertEquals(active,
			repository.findActiveUserIdForUpdate("usr_buddy_active").orElseThrow());
		assertTrue(repository.findActiveUserId("usr_buddy_deleted").isEmpty());
		assertTrue(repository.findActiveUserId("usr_buddy_missing").isEmpty());

		BuddyProfileRecord loaded = repository.findByUserId(active).orElseThrow();
		assertEquals(loaded, repository.findActiveById(loaded.profileId()).orElseThrow());
		assertEquals(List.of(PlaceLanguage.EN, PlaceLanguage.KO),
			loaded.profile().availableLanguages());
		assertEquals(List.of(BuddyStyle.FOODIE, BuddyStyle.PHOTOGRAPHY),
			loaded.profile().buddyStyles());
		assertFalse(loaded.profile().snsPublic());
		jdbcTemplate.update("UPDATE users SET deleted_at = NOW(6) WHERE id = ?", active);
		assertTrue(repository.findActiveById(loaded.profileId()).isEmpty());
	}

	private BuddyProfileDraft firstDraft() {
		return new BuddyProfileDraft(
			"https://cdn.example.com/emma.jpg",
			"Emma",
			"France",
			List.of(PlaceLanguage.EN, PlaceLanguage.KO),
			KoreanLevel.BEGINNER,
			"Local food fan",
			List.of(BuddyStyle.FOODIE, BuddyStyle.PHOTOGRAPHY),
			List.of(
				new BuddySocialLink(SocialLinkType.INSTAGRAM, "@emma"),
				new BuddySocialLink(SocialLinkType.KAKAOTALK, "emma-kakao")),
			true,
			false,
			true);
	}

	private BuddyProfileDraft secondDraft() {
		return new BuddyProfileDraft(
			null,
			"Emma Updated",
			null,
			List.of(PlaceLanguage.KO),
			KoreanLevel.ADVANCED,
			null,
			List.of(BuddyStyle.QUIET_TRAVEL),
			List.of(new BuddySocialLink(SocialLinkType.THREADS, "@emma_new")),
			false,
			false,
			false);
	}

	private long user(String publicId) {
		jdbcTemplate.update(
			"INSERT INTO users (public_id, signup_status) VALUES (?, 'COMPLETED')",
			publicId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private int count(String table, String key, long value) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM " + table + " WHERE " + key + " = ?",
			Integer.class,
			value);
	}
}
