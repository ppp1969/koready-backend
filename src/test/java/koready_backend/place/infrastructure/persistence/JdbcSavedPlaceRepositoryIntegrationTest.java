package koready_backend.place.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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

import koready_backend.place.application.port.SavedPlaceRepository;
import koready_backend.place.application.port.SavedPlaceRepository.SavedPlaceCriteria;
import koready_backend.place.application.port.SavedPlaceRepository.SavedPlaceCursor;
import koready_backend.place.application.port.SavedPlaceRepository.SavedPlaceRecord;
import koready_backend.place.application.port.SavedPlaceRepository.SavedPlaceRow;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.SavedPlaceSource;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcSavedPlaceRepositoryIntegrationTest {

	private static final Instant FIRST = Instant.parse("2026-07-19T01:00:00Z");
	private static final Instant SECOND = Instant.parse("2026-07-19T02:00:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 7, 19);

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SavedPlaceRepository repository;

	@BeforeEach
	void migrationIsApplied() {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '9'",
			Integer.class);
		assertEquals(1, count);
	}

	@Test
	void saveIsIdempotentAndAResaveUsesANewTimestampAndSource() {
		long userId = user("usr_saved_idempotent");
		long placeId = place("saved-idempotent", true, true, "저장 장소", "Saved Place");

		SavedPlaceRecord first = repository.save(
			userId, placeId, SavedPlaceSource.HOME_MONTHLY, FIRST);
		SavedPlaceRecord duplicate = repository.save(
			userId, placeId, SavedPlaceSource.MAP, SECOND);

		assertEquals(FIRST, first.savedAt());
		assertEquals(FIRST, duplicate.savedAt());
		assertEquals("HOME_MONTHLY", source(userId, placeId));
		assertEquals(1, savedRowCount(userId, placeId));

		repository.unsave(userId, placeId, SECOND);
		repository.unsave(userId, placeId, SECOND);
		SavedPlaceRecord restored = repository.save(
			userId, placeId, SavedPlaceSource.PLACE_DETAIL, SECOND);

		assertEquals(SECOND, restored.savedAt());
		assertEquals("PLACE_DETAIL", source(userId, placeId));
		assertEquals(1, savedRowCount(userId, placeId));
		assertEquals(0, deletedRowCount(userId, placeId));
	}

	@Test
	void listsOnlyActiveVisiblePlacesWithStableCursorAndEnglishFallback() {
		long userId = user("usr_saved_list");
		long koreanOnly = place("saved-ko", true, true, "한국어 장소", null);
		long translated = place("saved-en", true, true, "번역 장소", "English Place");
		long hidden = place("saved-hidden", true, false, "숨김 장소", "Hidden Place");

		repository.save(userId, koreanOnly, SavedPlaceSource.MAP, FIRST);
		repository.save(userId, translated, SavedPlaceSource.PLACE_DETAIL, SECOND);
		repository.save(userId, hidden, SavedPlaceSource.HOME_MONTHLY, SECOND.plusSeconds(1));

		List<SavedPlaceRow> firstPage = repository.findAll(new SavedPlaceCriteria(
			userId, null, 1, PlaceLanguage.EN, TODAY));
		SavedPlaceRow boundary = firstPage.getFirst();
		List<SavedPlaceRow> secondPage = repository.findAll(new SavedPlaceCriteria(
			userId,
			new SavedPlaceCursor(boundary.savedAt(), boundary.savedPlaceId()),
			2,
			PlaceLanguage.EN,
			TODAY));

		assertEquals(List.of(translated), firstPage.stream().map(SavedPlaceRow::placeId).toList());
		assertEquals("English Place", firstPage.getFirst().title());
		assertEquals(List.of(koreanOnly), secondPage.stream().map(SavedPlaceRow::placeId).toList());
		assertEquals("한국어 장소", secondPage.getFirst().title());
		assertTrue(repository.existsVisiblePlace(translated));
		assertFalse(repository.existsVisiblePlace(hidden));
	}

	@Test
	void resolvesOnlyActiveUsers() {
		long active = user("usr_saved_active");
		long deleted = user("usr_saved_deleted");
		jdbcTemplate.update("UPDATE users SET deleted_at = NOW(6) WHERE id = ?", deleted);

		assertEquals(active, repository.findActiveUserId("usr_saved_active").orElseThrow());
		assertTrue(repository.findActiveUserId("usr_saved_deleted").isEmpty());
		assertTrue(repository.findActiveUserId("usr_saved_missing").isEmpty());
	}

	private long user(String publicId) {
		jdbcTemplate.update(
			"INSERT INTO users (public_id, signup_status) VALUES (?, 'COMPLETED')",
			publicId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private long place(
		String contentId,
		boolean active,
		boolean showFlag,
		String koreanTitle,
		String englishTitle
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, service_region_code, show_flag, active, data_quality_score)
			VALUES (?, 'SEOUL', ?, ?, 90.00)
			""",
			contentId, showFlag, active);
		long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?", Long.class, contentId);
		localization(placeId, "KO", koreanTitle, "서울 종로구", "한국어 설명");
		if (englishTitle != null) {
			localization(placeId, "EN", englishTitle, "Jongno-gu, Seoul", "English overview");
		}
		return placeId;
	}

	private void localization(
		long placeId,
		String language,
		String title,
		String address,
		String overview
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, address_text, overview, translation_source)
			VALUES (?, ?, ?, ?, ?, 'MANUAL_EDITED')
			""",
			placeId, language, title, address, overview);
	}

	private String source(long userId, long placeId) {
		return jdbcTemplate.queryForObject(
			"SELECT source FROM user_saved_places WHERE user_id = ? AND place_id = ?",
			String.class,
			userId,
			placeId);
	}

	private int savedRowCount(long userId, long placeId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_saved_places WHERE user_id = ? AND place_id = ?",
			Integer.class,
			userId,
			placeId);
	}

	private int deletedRowCount(long userId, long placeId) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM user_saved_places
			WHERE user_id = ? AND place_id = ? AND deleted_at IS NOT NULL
			""",
			Integer.class,
			userId,
			placeId);
	}
}
