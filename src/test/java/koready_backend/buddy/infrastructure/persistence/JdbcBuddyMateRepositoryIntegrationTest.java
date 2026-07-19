package koready_backend.buddy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
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

import koready_backend.buddy.application.port.BuddyMateRepository;
import koready_backend.buddy.application.port.BuddyMateRepository.MateCursor;
import koready_backend.buddy.application.port.BuddyMateRepository.MateQuery;
import koready_backend.buddy.application.port.BuddyMateRepository.MateRow;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.domain.PlaceLanguage;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcBuddyMateRepositoryIntegrationTest {

	private static final Instant FIRST = Instant.parse("2026-07-19T01:00:00Z");
	private static final Instant SECOND = Instant.parse("2026-07-19T02:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BuddyMateRepository repository;

	@Test
	void flywayAddsThePlaceMateKeysetIndex() {
		String columns = jdbcTemplate.queryForObject(
			"""
			SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'user_saved_places'
			  AND index_name = 'idx_user_saved_places_place_mates'
			""",
			String.class);

		assertEquals("place_id,deleted_at,saved_at,id", columns);
	}

	@Test
	void returnsOnlyRecentPublicUnblockedSavedProfilesWithBatchedAssociations() {
		long placeId = place("MATE-PLACE-001", true, true);
		long requester = user("usr_mate_requester");
		profile(requester, "Requester", true, true, true, "@requester");
		save(requester, placeId, SECOND, false);

		long recent = user("usr_mate_recent");
		long recentProfile = profile(recent, "Recent", true, true, true, "@recent");
		save(recent, placeId, SECOND, false);

		long older = user("usr_mate_older");
		long olderProfile = profile(older, "Older", true, false, false, "@older");
		save(older, placeId, FIRST, false);

		long privateUser = user("usr_mate_private");
		profile(privateUser, "Private", false, true, true, "@private");
		save(privateUser, placeId, SECOND, false);

		long blockedByRequester = user("usr_mate_blocked_by_requester");
		profile(blockedByRequester, "Blocked One", true, true, true, "@blocked1");
		save(blockedByRequester, placeId, SECOND, false);
		block(requester, blockedByRequester);

		long blockedRequester = user("usr_mate_blocked_requester");
		profile(blockedRequester, "Blocked Two", true, true, true, "@blocked2");
		save(blockedRequester, placeId, SECOND, false);
		block(blockedRequester, requester);

		long unsaved = user("usr_mate_unsaved");
		profile(unsaved, "Unsaved", true, true, true, "@unsaved");
		save(unsaved, placeId, SECOND, true);

		long deleted = user("usr_mate_deleted");
		profile(deleted, "Deleted", true, true, true, "@deleted");
		save(deleted, placeId, SECOND, false);
		jdbcTemplate.update("UPDATE users SET deleted_at = NOW(6) WHERE id = ?", deleted);

		assertTrue(repository.existsVisiblePlace(placeId));
		assertFalse(repository.existsVisiblePlace(place("MATE-PLACE-HIDDEN", true, false)));

		List<MateRow> rows = repository.findAll(
			new MateQuery(requester, placeId, null, 10));

		assertEquals(List.of(recentProfile, olderProfile),
			rows.stream().map(row -> row.profile().profileId()).toList());
		assertEquals(List.of(PlaceLanguage.EN, PlaceLanguage.KO),
			rows.getFirst().profile().profile().availableLanguages());
		assertEquals(List.of(BuddyStyle.FOODIE, BuddyStyle.PHOTOGRAPHY),
			rows.getFirst().profile().profile().buddyStyles());
		assertEquals(List.of(
			new BuddySocialLink(SocialLinkType.INSTAGRAM, "@recent")),
			rows.getFirst().profile().profile().socialLinks());
		assertFalse(rows.get(1).profile().profile().snsPublic());

		List<MateRow> firstPage = repository.findAll(
			new MateQuery(requester, placeId, null, 1));
		MateRow last = firstPage.getFirst();
		List<MateRow> secondPage = repository.findAll(new MateQuery(
			requester,
			placeId,
			new MateCursor(last.savedAt(), last.savedPlaceId()),
			1));
		assertEquals(recentProfile, firstPage.getFirst().profile().profileId());
		assertEquals(olderProfile, secondPage.getFirst().profile().profileId());
	}

	private long user(String publicId) {
		jdbcTemplate.update(
			"INSERT INTO users (public_id, signup_status) VALUES (?, 'COMPLETED')",
			publicId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private long profile(
		long userId,
		String nickname,
		boolean profilePublic,
		boolean snsPublic,
		boolean allowsMessages,
		String socialValue
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_profiles
			    (user_id, nickname, nationality, korean_level, bio, profile_public,
			     sns_public, allows_messages, created_at, updated_at)
			VALUES (?, ?, 'France', 'BEGINNER', 'Travel mate', ?, ?, ?, NOW(6), NOW(6))
			""",
			userId,
			nickname,
			profilePublic,
			snsPublic,
			allowsMessages);
		long profileId = jdbcTemplate.queryForObject(
			"SELECT id FROM buddy_profiles WHERE user_id = ?", Long.class, userId);
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_profile_languages (profile_id, language_code, display_order)
			VALUES (?, 'EN', 1), (?, 'KO', 2)
			""",
			profileId,
			profileId);
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_profile_styles (profile_id, buddy_style, display_order)
			VALUES (?, 'FOODIE', 1), (?, 'PHOTOGRAPHY', 2)
			""",
			profileId,
			profileId);
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_social_links
			    (profile_id, link_type, link_value, display_order)
			VALUES (?, 'INSTAGRAM', ?, 1)
			""",
			profileId,
			socialValue);
		return profileId;
	}

	private long place(String contentId, boolean active, boolean showFlag) {
		jdbcTemplate.update(
			"INSERT INTO places (kto_content_id, active, show_flag) VALUES (?, ?, ?)",
			contentId,
			active,
			showFlag);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?", Long.class, contentId);
	}

	private void save(
		long userId,
		long placeId,
		Instant savedAt,
		boolean deleted
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO user_saved_places
			    (user_id, place_id, source, saved_at, updated_at, deleted_at)
			VALUES (?, ?, 'PLACE_DETAIL', ?, ?, ?)
			""",
			userId,
			placeId,
			Timestamp.from(savedAt),
			Timestamp.from(savedAt),
			deleted ? Timestamp.from(savedAt.plusSeconds(1)) : null);
	}

	private void block(long blocker, long blocked) {
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_blocks (blocker_user_id, blocked_user_id, created_at)
			VALUES (?, ?, NOW(6))
			""",
			blocker,
			blocked);
	}
}
