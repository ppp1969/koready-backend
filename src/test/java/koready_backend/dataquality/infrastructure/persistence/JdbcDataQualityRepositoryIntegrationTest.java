package koready_backend.dataquality.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
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

import koready_backend.dataquality.application.port.DataQualityRepository;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class JdbcDataQualityRepositoryIntegrationTest {

	private static final Instant LAST_SYNC = Instant.parse("2026-07-19T08:30:00.123456Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	DataQualityRepository repository;

	@Test
	void returnsZeroCountsAndNoSyncTimeForAnEmptyDatabase() {
		var result = repository.summarize();

		assertEquals(0, result.places().total());
		assertEquals(0, result.places().active());
		assertEquals(0, result.places().missingImage());
		assertEquals(0, result.places().missingEnglish());
		assertEquals(0, result.places().missingCoordinates());
		assertEquals(0, result.places().missingAddress());
		assertEquals(0, result.places().curationReady());
		assertEquals(0, result.localization().ktoEnglish());
		assertEquals(0, result.localization().aiTranslated());
		assertEquals(0, result.localization().manualEdited());
		assertNull(result.lastSuccessfulSyncAt());
	}

	@Test
	void aggregatesVisiblePlaceReadinessLocalizationSourcesAndLatestSync() {
		long completePlace = insertPlace(
			"quality-complete",
			"SEOUL",
			"Seoul address",
			35.1234567,
			128.1234567,
			"https://example.invalid/complete.jpg",
			true,
			true);
		insertLocalization(completePlace, "KO", "완전한 장소", null, "KTO_KO");
		insertLocalization(completePlace, "EN", "Complete place", null, "KTO_EN");

		long incompletePlace = insertPlace(
			"quality-incomplete", null, null, null, null, null, true, true);
		insertLocalization(incompletePlace, "KO", "보완할 장소", null, "KTO_KO");

		long translatedPlace = insertPlace(
			"quality-translated",
			"JEJU",
			null,
			33.1234567,
			126.1234567,
			"https://example.invalid/translated.jpg",
			true,
			true);
		insertLocalization(
			translatedPlace, "KO", "번역된 장소", "Jeju address", "MANUAL_EDITED");
		insertLocalization(
			translatedPlace, "EN", "Translated place", "Jeju address", "AI_TRANSLATED");

		insertPlace("quality-hidden", null, null, null, null, null, false, false);
		insertSyncCursor("areaBasedList2", LAST_SYNC.minusSeconds(3600));
		insertSyncCursor("searchFestival2", LAST_SYNC);

		var result = repository.summarize();

		assertEquals(4, result.places().total());
		assertEquals(3, result.places().active());
		assertEquals(1, result.places().missingImage());
		assertEquals(1, result.places().missingEnglish());
		assertEquals(1, result.places().missingCoordinates());
		assertEquals(1, result.places().missingAddress());
		assertEquals(2, result.places().curationReady());
		assertEquals(1, result.localization().ktoEnglish());
		assertEquals(1, result.localization().aiTranslated());
		assertEquals(1, result.localization().manualEdited());
		assertEquals(LAST_SYNC, result.lastSuccessfulSyncAt());
	}

	private long insertPlace(
		String contentId,
		String serviceRegionCode,
		String address,
		Double latitude,
		Double longitude,
		String firstImageUrl,
		boolean active,
		boolean showFlag
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, service_region_code, address, latitude, longitude,
			     first_image_url, active, show_flag)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			""",
			contentId,
			serviceRegionCode,
			address,
			latitude,
			longitude,
			firstImageUrl,
			active,
			showFlag);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertLocalization(
		long placeId,
		String language,
		String title,
		String address,
		String translationSource
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, address_text, translation_source)
			VALUES (?, ?, ?, ?, ?)
			""",
			placeId,
			language,
			title,
			address,
			translationSource);
	}

	private void insertSyncCursor(String operation, Instant lastSuccessAt) {
		jdbcTemplate.update(
			"""
			INSERT INTO tour_api_sync_cursors
			    (provider, api_name, operation, cursor_type, cursor_value, last_success_at)
			VALUES ('KTO', 'KorService2', ?, 'PAGE', '1', ?)
			""",
			operation,
			Timestamp.from(lastSuccessAt));
	}
}
