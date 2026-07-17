package koready_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class MySqlApplicationIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void verifyMigrationApplied() {
		Integer migrationCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1'",
			Integer.class);

		assertEquals(1, migrationCount);
	}

	@Test
	void contextLoadsWithMySql() {
	}

	@Test
	void seedsSevenServiceRegionsAndMapsIncheonToGyeonggi() {
		Integer serviceRegionCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM service_regions",
			Integer.class);
		String incheonServiceRegion = jdbcTemplate.queryForObject(
			"""
			SELECT service_region_code
			FROM administrative_regions
			WHERE provider = 'KTO' AND level = 'SIDO' AND code = '2'
			""",
			String.class);

		assertEquals(7, serviceRegionCount);
		assertEquals("GYEONGGI", incheonServiceRegion);
	}

	@Test
	@Transactional
	void rejectsDuplicateKtoPlace() {
		insertPlace("test-place-duplicate");

		assertThrows(
			DataIntegrityViolationException.class,
			() -> insertPlace("test-place-duplicate"));
	}

	@Test
	@Transactional
	void rejectsPlaceWithUnknownServiceRegion() {
		assertThrows(
			DataIntegrityViolationException.class,
			() -> jdbcTemplate.update(
				"""
				INSERT INTO places
					(kto_content_id, service_region_code, show_flag, active)
				VALUES ('test-unknown-region', 'UNKNOWN', TRUE, TRUE)
				"""));
	}

	@Test
	@Transactional
	void separatesFestivalOccurrencesByYearAndRejectsDuplicateYearSequence() {
		long placeId = insertPlace("test-festival-place");
		jdbcTemplate.update(
			"""
			INSERT INTO festival_series
				(series_key, canonical_place_id, title_ko, match_status)
			VALUES (?, ?, ?, ?)
			""",
			"test-festival-series",
			placeId,
			"테스트 축제",
			"MANUAL_CONFIRMED");
		Long seriesId = jdbcTemplate.queryForObject(
			"SELECT id FROM festival_series WHERE series_key = ?",
			Long.class,
			"test-festival-series");

		insertFestivalOccurrence(seriesId, placeId, 2026, "test-festival-2026");
		insertFestivalOccurrence(seriesId, placeId, 2027, "test-festival-2027");

		Integer occurrenceCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM place_event_occurrences WHERE festival_series_id = ?",
			Integer.class,
			seriesId);
		assertEquals(2, occurrenceCount);
		assertThrows(
			DataIntegrityViolationException.class,
			() -> insertFestivalOccurrence(seriesId, placeId, 2026, "test-festival-duplicate"));
	}

	@Test
	@Transactional
	void rejectsDuplicateSyncCursorAndBatchTarget() {
		jdbcTemplate.update(
			"""
			INSERT INTO tour_api_sync_cursors
				(provider, api_name, operation, cursor_type, cursor_value)
			VALUES ('KTO', 'KOR', 'areaBasedSyncList2', 'PAGE', '1')
			""");
		assertThrows(
			DataIntegrityViolationException.class,
			() -> jdbcTemplate.update(
				"""
				INSERT INTO tour_api_sync_cursors
					(provider, api_name, operation, cursor_type, cursor_value)
				VALUES ('KTO', 'KOR', 'areaBasedSyncList2', 'PAGE', '2')
				"""));

		jdbcTemplate.update(
			"""
			INSERT INTO batch_jobs (job_type, status, trigger_source)
			VALUES ('KTO_DAILY_SYNC', 'PENDING', 'SCHEDULED')
			""");
		Long jobId = jdbcTemplate.queryForObject(
			"SELECT MAX(id) FROM batch_jobs",
			Long.class);
		jdbcTemplate.update(
			"""
			INSERT INTO batch_job_items (batch_job_id, target_type, target_id, status)
			VALUES (?, 'API_PAGE', 'page-1', 'PENDING')
			""",
			jobId);
		assertThrows(
			DataIntegrityViolationException.class,
			() -> jdbcTemplate.update(
				"""
				INSERT INTO batch_job_items (batch_job_id, target_type, target_id, status)
				VALUES (?, 'API_PAGE', 'page-1', 'PENDING')
				""",
				jobId));
	}

	private long insertPlace(String ktoContentId) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
				(kto_content_id, service_region_code, show_flag, active)
			VALUES (?, 'SEOUL', TRUE, TRUE)
			""",
			ktoContentId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			ktoContentId);
	}

	private void insertFestivalOccurrence(
		long seriesId,
		long placeId,
		int eventYear,
		String sourceContentId
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_event_occurrences
				(festival_series_id, place_id, event_year, occurrence_sequence,
				 start_date, end_date, provider, source_content_id, source_operation,
				 visible_from, date_validation_status)
			VALUES (?, ?, ?, 1, ?, ?, 'KTO', ?, 'searchFestival2', ?, 'VALID')
			""",
			seriesId,
			placeId,
			eventYear,
			eventYear + "-10-10",
			eventYear + "-10-20",
			sourceContentId,
			eventYear + "-04-10");
	}
}
