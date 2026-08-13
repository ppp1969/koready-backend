package koready_backend.editorial.infrastructure.persistence;

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

import koready_backend.editorial.application.port.EditorialRepository;
import koready_backend.editorial.application.port.EditorialRepository.EnqueueCommand;
import koready_backend.editorial.application.port.EditorialWorkerRepository;
import koready_backend.editorial.application.port.EditorialWorkerRepository.ClaimCommand;
import koready_backend.editorial.application.port.EditorialWorkerRepository.CompleteCommand;
import koready_backend.editorial.domain.EditorialGeneration;
import koready_backend.editorial.domain.EditorialGeneration.LocalizedContent;
import koready_backend.editorial.domain.EditorialJobPriority;
import koready_backend.editorial.domain.EditorialTriggerType;
import koready_backend.editorial.domain.TourismPurposeTag;

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
			korean, english, List.of(TourismPurposeTag.LOCAL, TourismPurposeTag.EXPERIENCE),
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

	private long place() {
		jdbcTemplate.update("""
			INSERT INTO places
			    (kto_content_id, service_region_code, first_image_url,
			     source_modified_time, show_flag, active)
			VALUES ('editorial-place', 'SEOUL', 'https://example.com/image.jpg',
			        '20260813000000', TRUE, TRUE)
			""");
		long id = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = 'editorial-place'",
			Long.class);
		jdbcTemplate.update("""
			INSERT INTO place_localizations
			    (place_id, language, title, overview, translation_source, source_hash)
			VALUES (?, 'KO', '테스트 장소', '사실 기반 설명', 'KTO_KO', 'ko-hash'),
			       (?, 'EN', 'Test Place', NULL, 'KTO_EN', 'en-hash')
			""", id, id);
		jdbcTemplate.update("""
			INSERT INTO place_style_mappings
			    (place_id, travel_style, source, confidence, rule_version, is_primary)
			VALUES (?, 'CULTURE_EXPERIENCE', 'LCLS', 1.0, 'rule-v1', TRUE)
			""", id);
		return id;
	}
}
