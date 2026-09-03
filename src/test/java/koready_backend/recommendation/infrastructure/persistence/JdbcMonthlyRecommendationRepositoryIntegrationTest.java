package koready_backend.recommendation.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationCursor;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationFilter;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationPageQuery;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationRow;
import koready_backend.recommendation.domain.RecommendationSort;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcMonthlyRecommendationRepositoryIntegrationTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 7, 18);

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MonthlyRecommendationRepository repository;

	@BeforeEach
	void monthlyIndexMigrationIsApplied() {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4'",
			Integer.class);
		assertEquals(1, count);
	}

	@Test
	void includesEvergreenPlacesAndOrdersByActiveHeartsBeforeQuality() {
		long popular = evergreenPlace("popular-nature", "SEOUL", "40.00");
		long highQuality = evergreenPlace("high-quality-nature", "SEOUL", "99.00");
		insertAttribute(popular, "usetime", "09:00-17:00");
		long user1 = user("usr_monthly_heart_1");
		long user2 = user("usr_monthly_heart_2");
		save(user1, popular, false);
		save(user2, popular, false);
		save(user1, highQuality, true);

		MonthlyRecommendationFilter filter = filter(
			LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
			ServiceRegionCode.SEOUL, List.of(TravelStyle.NATURE),
			RecommendationSort.RECOMMENDED, PlaceLanguage.KO);

		List<MonthlyRecommendationRow> rows = repository.findPage(
			new MonthlyRecommendationPageQuery(filter, null, 20));

		assertEquals(List.of(popular, highQuality),
			rows.stream().map(MonthlyRecommendationRow::placeId).toList());
		assertEquals(2L, repository.count(filter));
		assertEquals("09:00-17:00", rows.getFirst().operatingHours());
	}

	@Test
	void findsOverlappingRoundsAndKeepsEndedOccurrence() {
		long endedPlace = festivalPlace("ended-2026", "JEOLLA", "95.00", true, true);
		long ended = occurrence(endedPlace, "same-festival", 2026,
			LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 2),
			LocalDate.of(2025, 12, 30));
		occurrence(endedPlace, "same-festival", 2025,
			LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 2),
			LocalDate.of(2025, 1, 1));

		long tooEarlyPlace = festivalPlace("too-early", "JEOLLA", "99.00", true, true);
		occurrence(tooEarlyPlace, "future-festival", 2027,
			LocalDate.of(2027, 1, 19), LocalDate.of(2027, 1, 20), TODAY.minusDays(1));

		long hiddenPlace = festivalPlace("hidden", "JEOLLA", "100.00", false, true);
		occurrence(hiddenPlace, "hidden-festival", 2026,
			LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 6),
			LocalDate.of(2026, 1, 5));

		MonthlyRecommendationFilter july = filter(
			LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
			ServiceRegionCode.JEOLLA, List.of(), RecommendationSort.RECOMMENDED,
			PlaceLanguage.EN);
		List<MonthlyRecommendationRow> rows = repository.findPage(
			new MonthlyRecommendationPageQuery(july, null, 20));

		assertEquals(List.of(ended), rows.stream().map(MonthlyRecommendationRow::occurrenceId).toList());
		assertEquals(2026, rows.getFirst().eventYear());
		assertEquals(0, rows.getFirst().statusRank());
		assertEquals("English ended-2026", rows.getFirst().title());
		assertEquals(1L, repository.count(july));

		MonthlyRecommendationFilter january = filter(
			LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31),
			ServiceRegionCode.JEOLLA, List.of(), RecommendationSort.RECOMMENDED,
			PlaceLanguage.KO);
		assertTrue(repository.findPage(
			new MonthlyRecommendationPageQuery(january, null, 20)).isEmpty());
	}

	@Test
	void appliesRecommendationOrderWithoutDemotingEndedFestivalAndContinuesCursor() {
		long ongoingPlace = festivalPlace("ongoing", "SEOUL", "50.00", true, true);
		long ongoing = occurrence(ongoingPlace, "ongoing", 2026,
			TODAY.minusDays(1), TODAY.plusDays(1), TODAY.minusMonths(6));
		long upcomingPlace = festivalPlace("upcoming", "SEOUL", "99.00", true, true);
		long upcoming = occurrence(upcomingPlace, "upcoming", 2026,
			TODAY.plusDays(2), TODAY.plusDays(3), TODAY.minusMonths(5));
		long endedPlace = festivalPlace("ended", "SEOUL", "100.00", true, true);
		long ended = occurrence(endedPlace, "ended", 2026,
			TODAY.minusDays(5), TODAY.minusDays(3), TODAY.minusMonths(6));

		MonthlyRecommendationFilter filter = filter(
			LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
			ServiceRegionCode.SEOUL, List.of(TravelStyle.LOCAL_FESTIVAL),
			RecommendationSort.RECOMMENDED, PlaceLanguage.EN);
		List<MonthlyRecommendationRow> first = repository.findPage(
			new MonthlyRecommendationPageQuery(filter, null, 1));
		assertEquals(List.of(ended), first.stream().map(MonthlyRecommendationRow::occurrenceId).toList());
		assertEquals("English ended", first.getFirst().title());
		assertEquals(3L, repository.count(filter));

		MonthlyRecommendationCursor cursor = new MonthlyRecommendationCursor(
			first.getFirst().statusRank(),
			first.getFirst().heartCount(),
			first.getFirst().qualityScore(),
			first.getFirst().endDate(),
			first.getFirst().occurrenceId());
		List<MonthlyRecommendationRow> rest = repository.findPage(
			new MonthlyRecommendationPageQuery(filter, cursor, 10));
		assertEquals(List.of(upcoming, ongoing),
			rest.stream().map(MonthlyRecommendationRow::occurrenceId).toList());
	}

	@Test
	void ordersByDeadlineAndContinuesAfterCursor() {
		long laterPlace = festivalPlace("deadline-later", "SEOUL", "100.00", true, true);
		long later = occurrence(laterPlace, "deadline-later", 2026,
			TODAY.plusDays(1), TODAY.plusDays(5), TODAY.minusMonths(5));
		long earlierPlace = festivalPlace("deadline-earlier", "SEOUL", "50.00", true, true);
		long earlier = occurrence(earlierPlace, "deadline-earlier", 2026,
			TODAY.plusDays(1), TODAY.plusDays(2), TODAY.minusMonths(5));

		MonthlyRecommendationFilter filter = filter(
			LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
			ServiceRegionCode.SEOUL, List.of(), RecommendationSort.DEADLINE,
			PlaceLanguage.KO);
		List<MonthlyRecommendationRow> first = repository.findPage(
			new MonthlyRecommendationPageQuery(filter, null, 1));
		assertEquals(List.of(earlier),
			first.stream().map(MonthlyRecommendationRow::occurrenceId).toList());

		MonthlyRecommendationRow last = first.getFirst();
		MonthlyRecommendationCursor cursor = new MonthlyRecommendationCursor(
			last.statusRank(), last.heartCount(), last.qualityScore(),
			last.endDate(), last.occurrenceId());
		List<MonthlyRecommendationRow> rest = repository.findPage(
			new MonthlyRecommendationPageQuery(filter, cursor, 10));
		assertEquals(List.of(later),
			rest.stream().map(MonthlyRecommendationRow::occurrenceId).toList());
	}

	private MonthlyRecommendationFilter filter(
		LocalDate start,
		LocalDate end,
		ServiceRegionCode region,
		List<TravelStyle> styles,
		RecommendationSort sort,
		PlaceLanguage language
	) {
		return new MonthlyRecommendationFilter(
			start, end, TODAY, region, styles, language, sort,
			sort == RecommendationSort.RECOMMENDED);
	}

	private long festivalPlace(
		String sourceId,
		String region,
		String score,
		boolean showFlag,
		boolean active
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, service_region_code, show_flag, active,
			     data_quality_score, first_image_url)
			VALUES (?, ?, ?, ?, ?, ?)
			""",
			sourceId, region, showFlag, active, new BigDecimal(score),
			"https://example.invalid/" + sourceId + ".jpg");
		long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?", Long.class, sourceId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, overview, address_text, translation_source)
			VALUES (?, 'KO', ?, 'Korean overview', 'Korean address', 'MANUAL_EDITED')
			""",
			placeId, "Korean " + sourceId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, overview, address_text, translation_source)
			VALUES (?, 'EN', ?, 'English overview', 'English address', 'MANUAL_EDITED')
			""",
			placeId, "English " + sourceId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_style_mappings (place_id, travel_style, source, confidence)
			VALUES (?, 'LOCAL_FESTIVAL', 'MANUAL', 1.0000)
			""",
			placeId);
		return placeId;
	}

	private long evergreenPlace(String sourceId, String region, String score) {
		long placeId = festivalPlace(sourceId, region, score, true, true);
		jdbcTemplate.update(
			"UPDATE place_style_mappings SET travel_style = 'NATURE' WHERE place_id = ?",
			placeId);
		return placeId;
	}

	private long user(String publicId) {
		jdbcTemplate.update(
			"INSERT INTO users (public_id, signup_status) VALUES (?, 'COMPLETED')",
			publicId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private void save(long userId, long placeId, boolean deleted) {
		jdbcTemplate.update(
			"""
			INSERT INTO user_saved_places
			    (user_id, place_id, source, saved_at, updated_at, deleted_at)
			VALUES (?, ?, 'HOME_MONTHLY', NOW(6), NOW(6), ?)
			""",
			userId, placeId, deleted ? java.time.LocalDateTime.now() : null);
	}

	private void insertAttribute(long placeId, String fieldCode, String value) {
		jdbcTemplate.update(
			"""
			INSERT INTO open_api_call_logs
			    (provider, api_name, operation, endpoint, request_started_at,
			     success, request_params_masked)
			VALUES ('KTO', 'KOR', 'detailIntro2', ?, UTC_TIMESTAMP(6),
			        TRUE, JSON_OBJECT())
			""",
			"https://example.invalid/monthly-hours-" + placeId);
		long callId = jdbcTemplate.queryForObject(
			"SELECT MAX(id) FROM open_api_call_logs", Long.class);
		jdbcTemplate.update(
			"""
			INSERT INTO open_api_raw_snapshots
			    (call_log_id, provider, api_name, operation, storage_key,
			     storage_format, content_type, raw_content_sha256,
			     stored_object_sha256, byte_size, compressed_byte_size,
			     item_count, captured_at, retention_class, immutable)
			VALUES (?, 'KTO', 'KOR', 'detailIntro2', ?, 'JSON_GZIP',
			        'application/json', ?, ?, 10, 10, 1, UTC_TIMESTAMP(6),
			        'DEBUG_TEMPORARY', TRUE)
			""",
			callId, "kto/test/monthly-hours-" + placeId,
			"d".repeat(64), "e".repeat(64));
		long snapshotId = jdbcTemplate.queryForObject(
			"SELECT id FROM open_api_raw_snapshots WHERE call_log_id = ?",
			Long.class, callId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_detail_attributes
			    (place_id, source_operation, item_sequence, field_code,
			     value_text, source_content_id, source_snapshot_id, source_hash)
			VALUES (?, 'detailIntro2', 1, ?, ?, 'monthly-hours', ?, ?)
			""",
			placeId, fieldCode, value, snapshotId, "c".repeat(64));
	}

	private long occurrence(
		long placeId,
		String sourceId,
		int eventYear,
		LocalDate start,
		LocalDate end,
		LocalDate visibleFrom
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_event_occurrences
			    (place_id, event_year, occurrence_sequence, start_date, end_date,
			     provider, source_content_id, source_operation, visible_from,
			     date_validation_status)
			VALUES (?, ?, 1, ?, ?, 'MANUAL', ?, 'INTEGRATION_TEST', ?, 'VALID')
			""",
			placeId, eventYear, start, end, sourceId, visibleFrom);
		return jdbcTemplate.queryForObject(
			"""
			SELECT id FROM place_event_occurrences
			WHERE provider = 'MANUAL' AND source_content_id = ? AND event_year = ?
			""",
			Long.class, sourceId, eventYear);
	}
}
