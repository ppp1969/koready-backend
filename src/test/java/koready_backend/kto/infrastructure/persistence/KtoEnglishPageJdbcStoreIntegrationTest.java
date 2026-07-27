package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.kto.application.model.KtoEnglishStorePageCommand;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoEnglishPageStore;
import koready_backend.kto.domain.KtoEnglishMatchDecision;
import koready_backend.kto.domain.KtoEnglishMatchMethod;
import koready_backend.kto.domain.KtoEnglishMatchStatus;
import koready_backend.kto.domain.KtoEnglishPlaceCandidate;
import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.domain.KtoEnglishSyncPage;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KtoEnglishPageJdbcStoreIntegrationTest {

	private static final Instant REQUESTED_AT = Instant.parse("2026-07-27T03:00:00Z");
	private static final Instant RECEIVED_AT = Instant.parse("2026-07-27T03:00:01Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	KtoEnglishPageStore pageStore;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanStoredPages() {
		jdbcTemplate.update("DELETE FROM place_source_matches");
		jdbcTemplate.update("DELETE FROM place_source_records");
		jdbcTemplate.update("DELETE FROM place_localizations");
		jdbcTemplate.update("DELETE FROM places");
		jdbcTemplate.update("DELETE FROM open_api_raw_snapshots");
		jdbcTemplate.update("DELETE FROM open_api_call_logs");
		jdbcTemplate.update("DELETE FROM batch_job_items");
		jdbcTemplate.update("DELETE FROM batch_jobs");
		jdbcTemplate.update("DELETE FROM tour_api_sync_cursors");
	}

	@Test
	void storesAutomaticReviewAndUnmatchedEnglishRecordsAndReplaysSafely() {
		long imagePlaceId = insertPlace("kor-image");
		long coordinatePlaceId = insertPlace("kor-coordinate");
		KtoEnglishPlaceItem image = item("eng-image", "old-image", "English image place");
		KtoEnglishPlaceItem coordinate = item("eng-coordinate", null, "English coordinate place");
		KtoEnglishPlaceItem review = item("eng-review", null, "Review place");
		KtoEnglishPlaceItem unmatched = item("eng-unmatched", null, "Unmatched place");
		List<KtoEnglishMatchDecision> decisions = List.of(
			decision(image, KtoEnglishMatchStatus.AUTO_CONFIRMED, KtoEnglishMatchMethod.IMAGE_PATH,
				List.of(candidate(imagePlaceId)), 1, 0),
			decision(coordinate, KtoEnglishMatchStatus.AUTO_CONFIRMED,
				KtoEnglishMatchMethod.COORDINATE_CONTENT_TYPE,
				List.of(candidate(coordinatePlaceId)), 0, 1),
			decision(review, KtoEnglishMatchStatus.REVIEW_REQUIRED,
				KtoEnglishMatchMethod.EVIDENCE_CONFLICT,
				List.of(candidate(imagePlaceId), candidate(coordinatePlaceId)), 1, 1),
			decision(unmatched, KtoEnglishMatchStatus.UNMATCHED,
				KtoEnglishMatchMethod.NONE, List.of(), 0, 0));
		KtoEnglishStorePageCommand command = command(1, "first", decisions);

		var first = pageStore.store(command);
		var replay = pageStore.store(command);

		assertFalse(first.replayed());
		assertTrue(replay.replayed());
		assertEquals(4, first.processedCount());
		assertEquals(2, first.autoMatchedCount());
		assertEquals(1, first.reviewRequiredCount());
		assertEquals(1, first.unmatchedCount());
		assertEquals(2, first.localizedCount());
		assertEquals(4, count("place_source_records"));
		assertEquals(4, count("place_source_matches"));
		assertEquals(2, count("place_localizations"));
		assertEquals(1, count("open_api_raw_snapshots"));
		assertEquals("old-image", jdbcTemplate.queryForObject(
			"SELECT source_old_content_id FROM place_source_records WHERE source_content_id = 'eng-image'",
			String.class));
		assertEquals("IMAGE_PATH", matchMethod("eng-image"));
		assertEquals("COORDINATE_CONTENT_TYPE", matchMethod("eng-coordinate"));
		assertEquals("USABLE", jdbcTemplate.queryForObject(
			"""
			SELECT source_quality
			FROM place_source_records
			WHERE source_content_id = 'eng-image'
			""",
			String.class));
		assertEquals(0, jdbcTemplate.queryForObject(
			"""
			SELECT JSON_LENGTH(quality_warnings)
			FROM place_source_records
			WHERE source_content_id = 'eng-image'
			""",
			Integer.class));
		assertEquals("kto-en-source-quality-v1", jdbcTemplate.queryForObject(
			"""
			SELECT quality_classifier_version
			FROM place_source_records
			WHERE source_content_id = 'eng-image'
			""",
			String.class));
		assertEquals(2, jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM place_source_matches matches
			JOIN place_source_records source ON source.id = matches.source_record_id
			WHERE source.source_content_id = 'eng-review'
			  AND matches.status = 'REVIEW_REQUIRED'
			""",
			Integer.class));
		assertEquals("1", jdbcTemplate.queryForObject(
			"""
			SELECT cursor_value
			FROM tour_api_sync_cursors
			WHERE api_name = 'ENG' AND operation = 'areaBasedSyncList2'
			""",
			String.class));
	}

	@Test
	void preservesManuallyEditedEnglishLocalization() {
		long placeId = insertPlace("kor-manual");
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
				(place_id, language, title, translation_source, source_content_id, source_hash)
			VALUES (?, 'EN', 'Approved title', 'MANUAL_EDITED', 'manual-source', ?)
			""",
			placeId,
			"f".repeat(64));
		KtoEnglishPlaceItem source = item("eng-manual", null, "Provider title");
		var decision = decision(
			source,
			KtoEnglishMatchStatus.AUTO_CONFIRMED,
			KtoEnglishMatchMethod.IMAGE_PATH,
			List.of(candidate(placeId)),
			1,
			0);

		var result = pageStore.store(command(2, "manual", List.of(decision)));

		assertEquals(0, result.localizedCount());
		assertEquals("Approved title", jdbcTemplate.queryForObject(
			"SELECT title FROM place_localizations WHERE place_id = ? AND language = 'EN'",
			String.class,
			placeId));
		assertEquals("MANUAL_EDITED", jdbcTemplate.queryForObject(
			"SELECT translation_source FROM place_localizations WHERE place_id = ? AND language = 'EN'",
			String.class,
			placeId));
	}

	private KtoEnglishStorePageCommand command(
		int pageNumber,
		String storageSuffix,
		List<KtoEnglishMatchDecision> decisions
	) {
		return new KtoEnglishStorePageCommand(
			new KtoEnglishSyncPage(
				pageNumber,
				200,
				25_348,
				decisions.stream().map(KtoEnglishMatchDecision::source).toList(),
				1_000,
				"a".repeat(64)),
			decisions,
			new KtoSuccessfulCallMetadata(REQUESTED_AT, RECEIVED_AT, 1_000, 200),
			new KtoStoredSnapshotMetadata(
				"kto/eng/areaBasedSyncList2/20260727/" + storageSuffix + ".json.gz",
				"b".repeat(64),
				500,
				RECEIVED_AT),
			null);
	}

	private long insertPlace(String contentId) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
				(kto_content_id, kto_content_type_id, service_region_code,
				 longitude, latitude, show_flag, active)
			VALUES (?, '12', 'SEOUL', 126.9780000, 37.5665000, TRUE, TRUE)
			""",
			contentId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			contentId);
	}

	private KtoEnglishPlaceItem item(String contentId, String oldContentId, String title) {
		return new KtoEnglishPlaceItem(
			contentId,
			oldContentId,
			"76",
			title,
			"Seoul",
			"Test road 1",
			"https://english.visitkorea.or.kr/images/test.jpg",
			null,
			"126.978",
			"37.5665",
			"20260727090000",
			"1",
			"c".repeat(64));
	}

	private KtoEnglishPlaceCandidate candidate(long placeId) {
		return new KtoEnglishPlaceCandidate(placeId, "image-key", "coordinate-key");
	}

	private KtoEnglishMatchDecision decision(
		KtoEnglishPlaceItem source,
		KtoEnglishMatchStatus status,
		KtoEnglishMatchMethod method,
		List<KtoEnglishPlaceCandidate> candidates,
		int imageCandidates,
		int coordinateCandidates
	) {
		return new KtoEnglishMatchDecision(
			source,
			status,
			method,
			candidates,
			imageCandidates,
			coordinateCandidates);
	}

	private String matchMethod(String sourceContentId) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT matches.match_method
			FROM place_source_matches matches
			JOIN place_source_records source ON source.id = matches.source_record_id
			WHERE source.source_content_id = ?
			""",
			String.class,
			sourceContentId);
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}
}
