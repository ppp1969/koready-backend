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
import koready_backend.buddy.application.port.ProfileImageRepository;
import koready_backend.buddy.application.port.ProfileImageRepository.ImageRecord;
import koready_backend.buddy.application.port.ProfileImageRepository.ImageStatus;
import koready_backend.buddy.domain.BuddyProfileDraft;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.buddy.domain.ProfileLanguage;
import koready_backend.place.domain.TravelStyle;

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

	@Autowired
	private ProfileImageRepository profileImages;

	@Test
	void createsAndFullyReplacesOneProfilePerUser() {
		long userId = user("usr_buddy_db");

		BuddyProfileRecord created = repository.save(userId, firstDraft(), FIRST);
		BuddyProfileRecord updated = repository.save(userId, secondDraft(), SECOND);

		assertEquals(created.profileId(), updated.profileId());
		assertEquals(FIRST, updated.createdAt());
		assertEquals(SECOND, updated.updatedAt());
		assertEquals("Emma Updated", updated.profile().nickname());
		assertEquals(List.of(ProfileLanguage.KO), updated.profile().availableLanguages());
		assertEquals(
			List.of(TravelStyle.NATURE),
			updated.profile().travelStyles());
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
		assertEquals(List.of(ProfileLanguage.VI, ProfileLanguage.KO),
			loaded.profile().availableLanguages());
		assertEquals(
			List.of(TravelStyle.LOCAL_FOOD, TravelStyle.CULTURE_EXPERIENCE),
			loaded.profile().travelStyles());
		assertEquals(List.of(BuddyStyle.FOODIE, BuddyStyle.PHOTOGRAPHY),
			loaded.profile().buddyStyles());
		assertFalse(loaded.profile().snsPublic());
		jdbcTemplate.update("UPDATE users SET deleted_at = NOW(6) WHERE id = ?", active);
		assertTrue(repository.findActiveById(loaded.profileId()).isEmpty());
	}

	@Test
	void exposesAReadyImageOnlyToItsOwnerOrThroughAPublicProfile() {
		long userId = user("usr_profile_image");
		String imageId = "img_11111111222233334444555555555555";
		String imagePath = "/api/v1/profile-images/" + imageId;
		profileImages.savePending(new ImageRecord(
			imageId,
			userId,
			"profile-images/usr_profile_image/image.jpg",
			"image/jpeg",
			1_024L,
			null,
			ImageStatus.PENDING,
			FIRST,
			null));
		profileImages.markReady(imageId, 1_000L, SECOND);
		repository.save(userId, draft(imagePath, false), SECOND);

		assertTrue(profileImages.findViewable(imageId, null).isEmpty());
		assertTrue(profileImages.findViewable(imageId, "usr_other").isEmpty());
		assertEquals(ImageStatus.READY,
			profileImages.findViewable(imageId, "usr_profile_image")
				.orElseThrow()
				.status());

		repository.save(userId, draft(imagePath, true), SECOND.plusSeconds(1));
		assertEquals(ImageStatus.READY,
			profileImages.findViewable(imageId, null).orElseThrow().status());
	}

	private BuddyProfileDraft firstDraft() {
		return new BuddyProfileDraft(
			"/api/v1/profile-images/img_11111111222233334444555555555555",
			"Emma",
			"FR",
			List.of(ProfileLanguage.VI, ProfileLanguage.KO),
			KoreanLevel.BEGINNER,
			List.of(TravelStyle.LOCAL_FOOD, TravelStyle.CULTURE_EXPERIENCE),
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
			"FR",
			List.of(ProfileLanguage.KO),
			KoreanLevel.ADVANCED,
			List.of(TravelStyle.NATURE),
			null,
			List.of(BuddyStyle.QUIET_TRAVEL),
			List.of(new BuddySocialLink(SocialLinkType.THREADS, "@emma_new")),
			false,
			false,
			false);
	}

	private BuddyProfileDraft draft(String imagePath, boolean profilePublic) {
		return new BuddyProfileDraft(
			imagePath,
			"Image Owner",
			"FR",
			List.of(ProfileLanguage.EN),
			KoreanLevel.INTERMEDIATE,
			List.of(TravelStyle.NATURE),
			"Looking for a travel buddy",
			List.of(),
			List.of(),
			profilePublic,
			false,
			true);
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
