package koready_backend.editorial.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

import koready_backend.editorial.application.port.EditorialRepository;
import koready_backend.editorial.application.port.EditorialRepository.EnqueueCommand;
import koready_backend.editorial.application.port.EditorialRepository.CandidateQuery;
import koready_backend.editorial.application.port.EditorialWorkerRepository;
import koready_backend.editorial.application.port.EditorialWorkerRepository.ClaimCommand;
import koready_backend.editorial.application.port.EditorialWorkerRepository.CompleteCommand;
import koready_backend.editorial.domain.EditorialGeneration;
import koready_backend.editorial.domain.EditorialGeneration.LocalizedContent;
import koready_backend.editorial.domain.EditorialJobPriority;
import koready_backend.editorial.domain.EditorialTriggerType;
import koready_backend.editorial.domain.TourismPurposeTag;
import koready_backend.editorial.domain.EditorialCandidateRegionFilter;
import koready_backend.editorial.domain.EditorialCandidateSourceTrack;
import koready_backend.editorial.domain.EditorialLanguage;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcEditorialRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private EditorialRepository repository;

	@Autowired
	private EditorialWorkerRepository workerRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void deduplicatesSameSourceAndUpgradesPmPriority() {
		long placeId = place();
		Instant now = Instant.parse("2026-08-13T00:00:00Z");

		var userJob = repository.enqueue(new EnqueueCommand(
			placeId, "prompt-v1", EditorialTriggerType.USER_DETAIL,
			EditorialJobPriority.NORMAL, null, now));
		var pmJob = repository.enqueue(new EnqueueCommand(
			placeId, "prompt-v1", EditorialTriggerType.PM_CURATED,
			EditorialJobPriority.HIGH, "admin", now.plusSeconds(1)));

		assertTrue(userJob.created());
		assertFalse(pmJob.created());
		assertEquals(userJob.jobId(), pmJob.jobId());
		assertEquals(EditorialJobPriority.HIGH, pmJob.priority());
		assertEquals(EditorialTriggerType.PM_CURATED, pmJob.triggerType());
		assertEquals(1, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM place_editorial_jobs WHERE place_id = ?",
			Integer.class, placeId));
	}

	@Test
	void claimsAndAtomicallyPublishesBilingualContent() {
		long placeId = place();
		Instant now = Instant.parse("2026-08-13T00:00:00Z");
		repository.enqueue(new EnqueueCommand(
			placeId, "prompt-v1", EditorialTriggerType.PM_CURATED,
			EditorialJobPriority.HIGH, "admin", now));

		var claimed = workerRepository.claimNext(new ClaimCommand(
			now, now.plusSeconds(300), "lease-1", 2)).orElseThrow();
		assertEquals(1, claimed.attemptCount());
		assertEquals("Test Place", claimed.source().titleEn());

		var korean = new LocalizedContent("주제", "한줄 설명", "간단 소개", List.of("하나", "둘", "셋"));
		var english = new LocalizedContent("Topic", "One line", "Introduction", List.of("One", "Two", "Three"));
		var generation = new EditorialGeneration(
			korean, english, "Generated title must not replace KTO", "Generated address",
			List.of(TourismPurposeTag.LOCAL, TourismPurposeTag.EXPERIENCE),
			"openai", "test-model", 10, 20);
		workerRepository.complete(new CompleteCommand(
			claimed.jobId(), claimed.leaseToken(), claimed.sourceFingerprint(),
			claimed.promptVersion(), generation, now.plusSeconds(2)));

		assertEquals("READY", jdbcTemplate.queryForObject(
			"SELECT status FROM place_editorial_jobs WHERE id = ?", String.class, claimed.jobId()));
		assertEquals(2, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM place_editorial_localizations", Integer.class));
		assertEquals(6, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM place_editorial_enjoy_points", Integer.class));
		assertEquals("Test Place", jdbcTemplate.queryForObject("""
			SELECT title FROM place_localizations WHERE place_id = ? AND language = 'EN'
			""", String.class, placeId));
		assertEquals("KTO_EN", jdbcTemplate.queryForObject("""
			SELECT translation_source FROM place_localizations
			WHERE place_id = ? AND language = 'EN'
			""", String.class, placeId));
	}

	@Test
	void recoversExpiredLeaseAndFailsAfterMaximumAttempts() {
		long placeId = place();
		Instant now = Instant.parse("2026-08-13T00:00:00Z");
		repository.enqueue(new EnqueueCommand(
			placeId, "prompt-v1", EditorialTriggerType.USER_DETAIL,
			EditorialJobPriority.NORMAL, null, now));

		var first = workerRepository.claimNext(new ClaimCommand(
			now, now.plusSeconds(1), "lease-1", 2)).orElseThrow();
		assertEquals(1, workerRepository.recoverExpiredLeases(now.plusSeconds(2), 2));
		assertEquals("QUEUED", jdbcTemplate.queryForObject(
			"SELECT status FROM place_editorial_jobs WHERE id = ?", String.class, first.jobId()));

		var second = workerRepository.claimNext(new ClaimCommand(
			now.plusSeconds(2), now.plusSeconds(3), "lease-2", 2)).orElseThrow();
		assertEquals(2, second.attemptCount());
		assertEquals(1, workerRepository.recoverExpiredLeases(now.plusSeconds(4), 2));
		assertEquals("FAILED", jdbcTemplate.queryForObject(
			"SELECT status FROM place_editorial_jobs WHERE id = ?", String.class, second.jobId()));
	}

	@Test
	void filtersCandidatesByIdRegionOverviewAndQueueEligibility() {
		long eligiblePlaceId = place("SEOUL", "사실 기반 설명");
		long noOverviewPlaceId = place("GYEONGGI", null);

		var eligibleQuery = new CandidateQuery(
			Long.toString(eligiblePlaceId), null, EditorialCandidateRegionFilter.SEOUL,
			true, true, EditorialCandidateSourceTrack.KTO_BILINGUAL, 0L, 20);
		var eligible = repository.findCandidates(eligibleQuery);

		assertEquals(1, eligible.size());
		assertEquals(eligiblePlaceId, eligible.getFirst().placeId());
		assertTrue(eligible.getFirst().queueEligible());
		assertEquals(1, repository.countCandidates(eligibleQuery));

		var noOverview = repository.findCandidates(new CandidateQuery(
			null, null, EditorialCandidateRegionFilter.GYEONGGI, false, false,
			EditorialCandidateSourceTrack.KTO_BILINGUAL, 0L, 20));
		assertEquals(List.of(noOverviewPlaceId), noOverview.stream()
			.map(EditorialRepository.CandidateRecord::placeId).toList());
		assertFalse(noOverview.getFirst().queueEligible());
	}

	@Test
	void separatesVerifiedBilingualAndKoreanOnlyAiCandidateTracks() {
		long bilingualPlaceId = place("SEOUL", "공식 한영 후보", true);
		long koreanOnlyPlaceId = place("GANGWON", "한국어 전용 후보", false);

		var bilingual = repository.findCandidates(candidateQuery(
			EditorialCandidateSourceTrack.KTO_BILINGUAL));
		var koreanOnly = repository.findCandidates(candidateQuery(
			EditorialCandidateSourceTrack.KOREAN_ONLY_AI));
		var all = repository.findCandidates(candidateQuery(EditorialCandidateSourceTrack.ALL));

		assertTrue(bilingual.stream().anyMatch(item -> item.placeId() == bilingualPlaceId));
		assertFalse(bilingual.stream().anyMatch(item -> item.placeId() == koreanOnlyPlaceId));
		assertEquals(List.of(koreanOnlyPlaceId), koreanOnly.stream()
			.map(EditorialRepository.CandidateRecord::placeId).toList());
		assertEquals(EditorialCandidateSourceTrack.KOREAN_ONLY_AI,
			koreanOnly.getFirst().sourceTrack());
		assertFalse(koreanOnly.getFirst().hasTrustedEnglish());
		assertTrue(all.stream().anyMatch(item -> item.placeId() == bilingualPlaceId));
		assertTrue(all.stream().anyMatch(item -> item.placeId() == koreanOnlyPlaceId));
	}

	@Test
	void claimsKoreanOnlyCandidateAndPublishesBilingualAiContent() {
		long placeId = place("SEOUL", "한국어 원문", false);
		Instant now = Instant.parse("2026-08-25T00:00:00Z");
		repository.enqueue(new EnqueueCommand(
			placeId, "prompt-v1", EditorialTriggerType.PM_CURATED,
			EditorialJobPriority.HIGH, "admin", now));

		var claimed = workerRepository.claimNext(new ClaimCommand(
			now, now.plusSeconds(300), "korean-only-lease", 2)).orElseThrow();
		assertEquals(null, claimed.source().titleEn());

		var generation = new EditorialGeneration(
			new LocalizedContent("주제", "한줄", "소개", List.of("하나", "둘", "셋")),
			new LocalizedContent("Topic", "One line", "Introduction",
				List.of("One", "Two", "Three")),
			"Korean Source Place", "Jongno-gu, Seoul",
			List.of(TourismPurposeTag.LOCAL, TourismPurposeTag.EXPERIENCE),
			"google-genai", "test-model", 10, 20);
		workerRepository.complete(new CompleteCommand(
			claimed.jobId(), claimed.leaseToken(), claimed.sourceFingerprint(),
			claimed.promptVersion(), generation, now.plusSeconds(2)));

		assertEquals(2, jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM place_editorial_localizations localized
			JOIN place_editorial_contents content
			  ON content.id = localized.editorial_content_id
			WHERE content.place_id = ?
			""", Integer.class, placeId));
		assertEquals("Korean Source Place", jdbcTemplate.queryForObject("""
			SELECT title FROM place_localizations WHERE place_id = ? AND language = 'EN'
			""", String.class, placeId));
		assertEquals("Jongno-gu, Seoul", jdbcTemplate.queryForObject("""
			SELECT address_text FROM place_localizations WHERE place_id = ? AND language = 'EN'
			""", String.class, placeId));
		assertEquals("Introduction", jdbcTemplate.queryForObject("""
			SELECT overview FROM place_localizations WHERE place_id = ? AND language = 'EN'
			""", String.class, placeId));
		assertEquals("AI_TRANSLATED", jdbcTemplate.queryForObject("""
			SELECT translation_source FROM place_localizations
			WHERE place_id = ? AND language = 'EN'
			""", String.class, placeId));
		assertTrue(repository.findReady(placeId, EditorialLanguage.EN, "prompt-v1").isPresent());
		var candidate = repository.findCandidate(placeId).orElseThrow();
		assertEquals("Korean Source Place", candidate.titleEn());
		assertEquals(EditorialCandidateSourceTrack.KOREAN_ONLY_AI, candidate.sourceTrack());
		assertFalse(candidate.hasTrustedEnglish());
	}

	private long place() {
		return place("SEOUL", "사실 기반 설명");
	}

	private long place(String region, String overview) {
		return place(region, overview, true);
	}

	private long place(String region, String overview, boolean includeEnglish) {
		String contentId = "editorial-place-" + UUID.randomUUID();
		jdbcTemplate.update("""
			INSERT INTO places
			    (kto_content_id, service_region_code, first_image_url,
			     source_modified_time, show_flag, active)
			VALUES (?, ?, 'https://example.com/image.jpg',
			        '20260813000000', TRUE, TRUE)
			""", contentId, region);
		long id = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?", Long.class, contentId);
		jdbcTemplate.update("""
			INSERT INTO place_localizations
			    (place_id, language, title, overview, address_text,
			     translation_source, source_hash)
			VALUES (?, 'KO', '테스트 장소', ?, '서울특별시 종로구', 'KTO_KO', 'ko-hash')
			""", id, overview);
		if (includeEnglish) {
			jdbcTemplate.update("""
				INSERT INTO place_localizations
				    (place_id, language, title, overview, translation_source, source_hash)
				VALUES (?, 'EN', 'Test Place', NULL, 'KTO_EN', 'en-hash')
				""", id);
		}
		jdbcTemplate.update("""
			INSERT INTO place_style_mappings
			    (place_id, travel_style, source, confidence, rule_version, is_primary)
			VALUES (?, 'CULTURE_EXPERIENCE', 'LCLS', 1.0, 'rule-v1', TRUE)
			""", id);
		return id;
	}

	private static CandidateQuery candidateQuery(EditorialCandidateSourceTrack sourceTrack) {
		return new CandidateQuery(null, null, null, null, null, sourceTrack, 0L, 100);
	}
}
