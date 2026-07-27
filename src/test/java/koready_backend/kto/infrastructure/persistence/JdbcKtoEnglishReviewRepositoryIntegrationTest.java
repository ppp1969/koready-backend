package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

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

import koready_backend.kto.application.exception.KtoEnglishReviewCandidateRequiredException;
import koready_backend.kto.application.exception.KtoEnglishReviewConflictException;
import koready_backend.kto.application.port.KtoEnglishReviewRepository;
import koready_backend.kto.application.port.KtoEnglishQualityRepository;
import koready_backend.kto.application.port.KtoEnglishQualityRepository.QualityUpdate;
import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.domain.KtoEnglishReviewDecision;
import koready_backend.kto.domain.KtoEnglishReviewStatus;
import koready_backend.kto.domain.KtoEnglishSourceQuality;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JdbcKtoEnglishReviewRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	KtoEnglishReviewRepository repository;

	@Autowired
	KtoEnglishQualityRepository qualityRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clean() {
		jdbcTemplate.update("DELETE FROM place_source_review_audits");
		jdbcTemplate.update("DELETE FROM place_source_review_decisions");
		jdbcTemplate.update("DELETE FROM place_source_matches");
		jdbcTemplate.update("DELETE FROM place_source_records");
		jdbcTemplate.update("DELETE FROM place_localizations");
		jdbcTemplate.update("DELETE FROM places");
		jdbcTemplate.update("DELETE FROM open_api_raw_snapshots");
		jdbcTemplate.update("DELETE FROM open_api_call_logs");
	}

	@Test
	void listsPendingSourcesAndConfirmsOnlyAnEvidenceCandidate() {
		long firstPlaceId = insertPlace("kor-1", "후보 장소 1");
		long secondPlaceId = insertPlace("kor-2", "후보 장소 2");
		long sourceRecordId = insertSource("eng-review", "a".repeat(64));
		insertCandidate(sourceRecordId, firstPlaceId);
		insertCandidate(sourceRecordId, secondPlaceId);

		var page = repository.findPage(new KtoEnglishReviewRepository.ReviewCriteria(
			null, null, null, null, 20));
		assertEquals(1, page.size());
		assertEquals(KtoEnglishReviewStatus.REVIEW_REQUIRED, page.getFirst().status());
		assertEquals(2, page.getFirst().candidateCount());
		var detailBeforeDecision = repository.findBySourceRecordId(sourceRecordId).orElseThrow();
		assertEquals(1, detailBeforeDecision.candidates().getFirst().imageCandidateCount());
		assertEquals(2, detailBeforeDecision.candidates().getFirst().coordinateCandidateCount());
		assertTrue(detailBeforeDecision.candidates().getFirst().evidenceConflict());

		KtoEnglishPlaceItem source = source("eng-review", "Provider title", "a".repeat(64));
		var decided = repository.review(new KtoEnglishReviewRepository.ReviewCommand(
			sourceRecordId,
			KtoEnglishReviewDecision.MANUAL_CONFIRMED,
			firstPlaceId,
			0,
			"operator-1",
			"이미지 경로와 좌표가 후보 1과 일치",
			source));

		assertEquals(KtoEnglishReviewStatus.MANUAL_CONFIRMED, decided.status());
		assertEquals(1, decided.version());
		assertEquals("Provider title", jdbcTemplate.queryForObject(
			"SELECT title FROM place_localizations WHERE place_id = ? AND language = 'EN'",
			String.class,
			firstPlaceId));
		assertEquals("KTO_EN", jdbcTemplate.queryForObject(
			"""
			SELECT translation_source FROM place_localizations
			WHERE place_id = ? AND language = 'EN'
			""",
			String.class,
			firstPlaceId));
		assertEquals(1, count("place_source_review_audits"));
		assertEquals(1, countStatus(sourceRecordId, "MANUAL_CONFIRMED"));
		assertEquals(1, countStatus(sourceRecordId, "REJECTED"));

		assertThrows(KtoEnglishReviewConflictException.class, () ->
			repository.review(new KtoEnglishReviewRepository.ReviewCommand(
				sourceRecordId,
				KtoEnglishReviewDecision.REJECTED,
				null,
				0,
				"operator-2",
				"오래된 화면에서 요청",
				source)));
	}

	@Test
	void rejectsArbitraryPlaceAndPreservesManualLocalization() {
		long candidatePlaceId = insertPlace("kor-candidate", "후보 장소");
		long arbitraryPlaceId = insertPlace("kor-arbitrary", "임의 장소");
		long sourceRecordId = insertSource("eng-manual", "b".repeat(64));
		insertCandidate(sourceRecordId, candidatePlaceId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, translation_source,
			     source_content_id, source_hash)
			VALUES (?, 'EN', 'Editor approved', 'MANUAL_EDITED', 'manual', ?)
			""",
			candidatePlaceId,
			"f".repeat(64));
		KtoEnglishPlaceItem source = source(
			"eng-manual", "Provider replacement", "b".repeat(64));

		assertThrows(KtoEnglishReviewCandidateRequiredException.class, () ->
			repository.review(new KtoEnglishReviewRepository.ReviewCommand(
				sourceRecordId,
				KtoEnglishReviewDecision.MANUAL_CONFIRMED,
				arbitraryPlaceId,
				0,
				"operator",
				"근거 없는 선택",
				source)));

		repository.review(new KtoEnglishReviewRepository.ReviewCommand(
			sourceRecordId,
			KtoEnglishReviewDecision.MANUAL_CONFIRMED,
			candidatePlaceId,
			0,
			"operator",
			"검증된 후보 선택",
			source));

		assertEquals("Editor approved", jdbcTemplate.queryForObject(
			"SELECT title FROM place_localizations WHERE place_id = ? AND language = 'EN'",
			String.class,
			candidatePlaceId));
		assertEquals("MANUAL_EDITED", jdbcTemplate.queryForObject(
			"""
			SELECT translation_source FROM place_localizations
			WHERE place_id = ? AND language = 'EN'
			""",
			String.class,
			candidatePlaceId));
	}

	@Test
	void exposesAndRejectsUnmatchedSourceWithAudit() {
		long sourceRecordId = insertSource("eng-unmatched", "c".repeat(64));

		var page = repository.findPage(new KtoEnglishReviewRepository.ReviewCriteria(
			KtoEnglishReviewStatus.UNMATCHED, null, "eng-unmatched", null, 20));
		assertEquals(1, page.size());
		assertEquals(0, page.getFirst().candidateCount());

		var decided = repository.review(new KtoEnglishReviewRepository.ReviewCommand(
			sourceRecordId,
			KtoEnglishReviewDecision.REJECTED,
			null,
			0,
			"operator",
			"매칭 근거 없음",
			source("eng-unmatched", "Unknown place", "c".repeat(64))));

		assertEquals(KtoEnglishReviewStatus.REJECTED, decided.status());
		var detail = repository.findBySourceRecordId(sourceRecordId).orElseThrow();
		assertTrue(detail.candidates().isEmpty());
		assertEquals(1, detail.audits().size());
		assertEquals("operator", detail.audits().getFirst().reviewedBy());
	}

	@Test
	void filtersLatestReviewSourcesByIndexedQuality() {
		long usableId = insertSource("eng-usable", "d".repeat(64));
		long suspectId = insertSource("eng-suspect", "e".repeat(64));
		jdbcTemplate.update(
			"""
			UPDATE place_source_records
			SET source_quality = 'USABLE',
			    quality_warnings = JSON_ARRAY(),
			    quality_classified_at = CURRENT_TIMESTAMP(6),
			    quality_classifier_version = 'kto-en-source-quality-v1'
			WHERE id = ?
			""",
			usableId);
		jdbcTemplate.update(
			"""
			UPDATE place_source_records
			SET source_quality = 'NON_ENGLISH_SUSPECTED',
			    quality_warnings = JSON_ARRAY('NON_LATIN_TITLE'),
			    quality_classified_at = CURRENT_TIMESTAMP(6),
			    quality_classifier_version = 'kto-en-source-quality-v1'
			WHERE id = ?
			""",
			suspectId);

		var page = repository.findPage(new KtoEnglishReviewRepository.ReviewCriteria(
			null,
			KtoEnglishSourceQuality.NON_ENGLISH_SUSPECTED,
			null,
			null,
			20));

		assertEquals(1, page.size());
		assertEquals(suspectId, page.getFirst().sourceRecordId());
		assertEquals(
			KtoEnglishSourceQuality.NON_ENGLISH_SUSPECTED,
			page.getFirst().sourceQuality());
		assertEquals(
			Set.of(koready_backend.kto.domain.KtoEnglishSourceQualityWarning.NON_LATIN_TITLE),
			page.getFirst().qualityWarnings());
	}

	@Test
	void backfillsOnlyUnclassifiedLatestSourcesAndDoesNotSelectThemAgain() {
		long firstId = insertSource("eng-first", "1".repeat(64));
		long secondId = insertSource("eng-second", "2".repeat(64));

		var initial = qualityRepository.findUnclassified(0L, 10);
		assertEquals(List.of(firstId, secondId), initial.stream()
			.map(KtoEnglishQualityRepository.QualityTarget::sourceRecordId)
			.toList());

		qualityRepository.classify(new QualityUpdate(
			firstId,
			"1".repeat(64),
			KtoEnglishSourceQuality.USABLE,
			Set.of(),
			Instant.parse("2026-07-27T02:00:00Z"),
			"kto-en-source-quality-v1"));

		var remaining = qualityRepository.findUnclassified(0L, 10);
		assertEquals(List.of(secondId), remaining.stream()
			.map(KtoEnglishQualityRepository.QualityTarget::sourceRecordId)
			.toList());
		var coverage = qualityRepository.summarizeLatest();
		assertEquals(2, coverage.total());
		assertEquals(1, coverage.classified());
		assertEquals(1, coverage.pending());
		assertEquals(1, coverage.usable());
	}

	private long insertPlace(String contentId, String title) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, kto_content_type_id, service_region_code,
			     address, latitude, longitude, first_image_url, show_flag, active)
			VALUES (?, '12', 'SEOUL', '서울 테스트 주소', 37.5700000, 126.9800000,
			        'https://example.com/place.jpg', TRUE, TRUE)
			""",
			contentId);
		long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			contentId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, address_text, translation_source,
			     source_content_id, source_hash)
			VALUES (?, 'KO', ?, '서울 테스트 주소', 'KTO_KO', ?, ?)
			""",
			placeId,
			title,
			contentId,
			"d".repeat(64));
		return placeId;
	}

	private long insertSource(String contentId, String sourceHash) {
		jdbcTemplate.update(
			"""
			INSERT INTO open_api_call_logs
			    (provider, api_name, operation, endpoint, request_started_at,
			     response_received_at, duration_ms, success, http_status,
			     item_count, response_bytes)
			VALUES ('KTO', 'ENG', 'areaBasedSyncList2', 'https://apis.example',
			        '2026-07-27 01:00:00', '2026-07-27 01:00:01', 1000,
			        TRUE, 200, 1, 1000)
			""");
		long callId = jdbcTemplate.queryForObject(
			"SELECT MAX(id) FROM open_api_call_logs",
			Long.class);
		jdbcTemplate.update(
			"""
			INSERT INTO open_api_raw_snapshots
			    (call_log_id, provider, api_name, operation, storage_key,
			     storage_format, content_type, raw_content_sha256,
			     stored_object_sha256, byte_size, compressed_byte_size,
			     item_count, captured_at, retention_class, immutable)
			VALUES (?, 'KTO', 'ENG', 'areaBasedSyncList2', ?,
			        'JSON_GZIP', 'application/json', ?, ?, 1000, 500, 1,
			        '2026-07-27 01:00:01', 'COMPETITION_EVIDENCE', TRUE)
			""",
			callId,
			"kto/eng/areaBasedSyncList2/20260727/" + contentId + ".json.gz",
			"e".repeat(64),
			"f".repeat(64));
		long snapshotId = jdbcTemplate.queryForObject(
			"SELECT id FROM open_api_raw_snapshots WHERE call_log_id = ?",
			Long.class,
			callId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_source_records
			    (provider, api_name, operation, source_content_id, language,
			     raw_snapshot_id, source_hash, captured_at)
			VALUES ('KTO', 'ENG', 'areaBasedSyncList2', ?, 'EN', ?, ?,
			        '2026-07-27 01:00:01')
			""",
			contentId,
			snapshotId,
			sourceHash);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM place_source_records WHERE source_content_id = ?",
			Long.class,
			contentId);
	}

	private void insertCandidate(long sourceRecordId, long placeId) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_source_matches
			    (source_record_id, place_id, match_method, confidence,
			     candidate_count, evidence_json, status, matcher_version)
			VALUES (?, ?, 'EVIDENCE_CONFLICT', 0.8000, 2,
			        JSON_OBJECT('imageCandidateCount', 1,
			                    'coordinateCandidateCount', 2,
			                    'conflict', TRUE),
			        'REVIEW_REQUIRED', 'test-v1')
			""",
			sourceRecordId,
			placeId);
	}

	private KtoEnglishPlaceItem source(String contentId, String title, String hash) {
		return new KtoEnglishPlaceItem(
			contentId,
			null,
			"12",
			title,
			"88 Test-ro",
			"Seoul",
			"https://example.com/source.jpg",
			null,
			"126.98",
			"37.57",
			"20260727010001",
			"1",
			hash);
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private int countStatus(long sourceRecordId, String status) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*) FROM place_source_matches
			WHERE source_record_id = ? AND status = ?
			""",
			Integer.class,
			sourceRecordId,
			status);
	}
}
