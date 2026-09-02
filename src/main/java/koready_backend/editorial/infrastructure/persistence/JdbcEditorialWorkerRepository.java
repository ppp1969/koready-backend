package koready_backend.editorial.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.editorial.application.port.EditorialWorkerRepository;
import koready_backend.editorial.domain.EditorialGeneration;
import koready_backend.editorial.domain.EditorialLanguage;
import koready_backend.place.domain.EnglishPlaceTitleNormalizer;

@Repository
public class JdbcEditorialWorkerRepository implements EditorialWorkerRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcEditorialWorkerRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public long countStartedBetween(Instant startInclusive, Instant endExclusive) {
		Long count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM place_editorial_audits
			WHERE action = 'EDITORIAL_PROCESSING_STARTED'
			  AND created_at >= ? AND created_at < ?
			""", Long.class, Timestamp.from(startInclusive), Timestamp.from(endExclusive));
		return count == null ? 0L : count;
	}

	@Override
	@Transactional
	public Optional<ClaimedJob> claimNext(ClaimCommand command) {
		List<Long> ids = jdbcTemplate.queryForList("""
			SELECT id FROM place_editorial_jobs
			WHERE status = 'QUEUED'
			  AND attempt_count < ?
			  AND COALESCE(next_attempt_at, requested_at) <= ?
			ORDER BY priority DESC, requested_at, id
			LIMIT 1
			FOR UPDATE SKIP LOCKED
			""", Long.class, command.maxAttempts(), Timestamp.from(command.now()));
		if (ids.isEmpty()) {
			return Optional.empty();
		}
		long jobId = ids.getFirst();
		jdbcTemplate.update("""
			UPDATE place_editorial_jobs
			SET status = 'PROCESSING', attempt_count = attempt_count + 1,
			    lease_token = ?, lease_expires_at = ?, next_attempt_at = NULL,
			    started_at = ?, completed_at = NULL,
			    error_code = NULL, error_message = NULL
			WHERE id = ?
			""", command.leaseToken(), Timestamp.from(command.leaseExpiresAt()),
			Timestamp.from(command.now()), jobId);
		jdbcTemplate.update("""
			INSERT INTO place_editorial_audits
			    (place_id, job_id, action, details_json, created_at)
			SELECT place_id, id, 'EDITORIAL_PROCESSING_STARTED',
			       JSON_OBJECT('attemptCount', attempt_count), ?
			FROM place_editorial_jobs WHERE id = ?
			""", Timestamp.from(command.now()), jobId);
		Optional<ClaimedJob> claimed = findClaimed(jobId);
		if (claimed.isPresent()
			&& !claimed.get().sourceFingerprint().equals(currentFingerprint(claimed.get().placeId()))) {
			markStale(jobId, claimed.get().placeId(), command.now());
			return Optional.empty();
		}
		return claimed;
	}

	@Override
	@Transactional
	public void complete(CompleteCommand command) {
		LockedJob job = lockOwnedJob(command.jobId(), command.leaseToken());
		if (!command.sourceFingerprint().equals(currentFingerprint(job.placeId()))) {
			markStale(command.jobId(), job.placeId(), command.completedAt());
			return;
		}
		EditorialGeneration generation = command.generation();
		jdbcTemplate.update("""
			INSERT INTO place_editorial_contents
			    (place_id, source_fingerprint, prompt_version, status,
			     provider, model, generated_at)
			VALUES (?, ?, ?, 'READY', ?, ?, ?)
			ON DUPLICATE KEY UPDATE
			    status = 'READY', provider = VALUES(provider), model = VALUES(model),
			    generated_at = VALUES(generated_at)
			""", job.placeId(), command.sourceFingerprint(), command.promptVersion(),
			generation.provider(), generation.model(), Timestamp.from(command.completedAt()));
		Long contentId = jdbcTemplate.queryForObject("""
			SELECT id FROM place_editorial_contents
			WHERE place_id = ? AND source_fingerprint = ? AND prompt_version = ?
			""", Long.class, job.placeId(), command.sourceFingerprint(), command.promptVersion());
		if (contentId == null) {
			throw new IllegalStateException("Editorial content was not persisted");
		}
		jdbcTemplate.update("DELETE FROM place_editorial_localizations WHERE editorial_content_id = ?", contentId);
		jdbcTemplate.update("DELETE FROM place_editorial_tags WHERE editorial_content_id = ?", contentId);
		jdbcTemplate.update("DELETE FROM place_editorial_enjoy_points WHERE editorial_content_id = ?", contentId);
		insertLocalization(contentId, EditorialLanguage.KO, generation.korean());
		insertLocalization(contentId, EditorialLanguage.EN, generation.english());
		upsertAiEnglishLocalization(
			job.placeId(), generation.titleEn(), generation.addressEn(),
			generation.english().shortIntroduction(), command.completedAt());
		for (int index = 0; index < generation.tags().size(); index++) {
			jdbcTemplate.update("""
				INSERT INTO place_editorial_tags
				    (editorial_content_id, display_order, tag_code) VALUES (?, ?, ?)
				""", contentId, index + 1, generation.tags().get(index).name());
		}
		int updated = jdbcTemplate.update("""
			UPDATE place_editorial_jobs
			SET status = 'READY', lease_token = NULL, lease_expires_at = NULL,
			    completed_at = ?, provider = ?, model = ?,
			    input_tokens = ?, output_tokens = ?
			WHERE id = ? AND status = 'PROCESSING' AND lease_token = ?
			""", Timestamp.from(command.completedAt()), generation.provider(), generation.model(),
			generation.inputTokens(), generation.outputTokens(), command.jobId(), command.leaseToken());
		if (updated != 1) {
			throw new IllegalStateException("Editorial job lease is no longer owned");
		}
		jdbcTemplate.update("""
			INSERT INTO place_editorial_audits
			    (place_id, job_id, action, details_json, created_at)
			VALUES (?, ?, 'EDITORIAL_READY',
			        JSON_OBJECT('provider', ?, 'model', ?, 'inputTokens', ?, 'outputTokens', ?), ?)
			""", job.placeId(), command.jobId(), generation.provider(), generation.model(),
			generation.inputTokens(), generation.outputTokens(), Timestamp.from(command.completedAt()));
	}

	@Override
	@Transactional
	public void fail(FailCommand command) {
		LockedJob job = lockOwnedJob(command.jobId(), command.leaseToken());
		String status = command.retry() ? "QUEUED" : "FAILED";
		int updated = jdbcTemplate.update("""
			UPDATE place_editorial_jobs
			SET status = ?, lease_token = NULL, lease_expires_at = NULL,
			    next_attempt_at = ?, completed_at = ?, error_code = ?, error_message = ?
			WHERE id = ? AND status = 'PROCESSING' AND lease_token = ?
			""", status, timestamp(command.nextAttemptAt()),
			command.retry() ? null : Timestamp.from(command.failedAt()),
			command.errorCode(), command.errorMessage(), command.jobId(), command.leaseToken());
		if (updated != 1) {
			throw new IllegalStateException("Editorial job lease is no longer owned");
		}
		jdbcTemplate.update("""
			INSERT INTO place_editorial_audits
			    (place_id, job_id, action, details_json, created_at)
			VALUES (?, ?, ?, JSON_OBJECT('errorCode', ?, 'retry', ?), ?)
			""", job.placeId(), command.jobId(),
			command.retry() ? "EDITORIAL_RETRY_QUEUED" : "EDITORIAL_FAILED",
			command.errorCode(), command.retry(), Timestamp.from(command.failedAt()));
	}

	@Override
	@Transactional
	public int recoverExpiredLeases(Instant now, int maxAttempts) {
		return jdbcTemplate.update("""
			UPDATE place_editorial_jobs
			SET status = IF(attempt_count < ?, 'QUEUED', 'FAILED'),
			    lease_token = NULL, lease_expires_at = NULL,
			    next_attempt_at = IF(attempt_count < ?, ?, NULL),
			    completed_at = IF(attempt_count < ?, NULL, ?),
			    error_code = 'WORKER_LEASE_EXPIRED',
			    error_message = 'Worker lease expired before completion'
			WHERE status = 'PROCESSING' AND lease_expires_at <= ?
			""", maxAttempts, maxAttempts, Timestamp.from(now), maxAttempts,
			Timestamp.from(now), Timestamp.from(now));
	}

	private Optional<ClaimedJob> findClaimed(long jobId) {
		List<ClaimedBase> rows = jdbcTemplate.query("""
			SELECT j.id, j.public_id, j.place_id, j.source_fingerprint,
			       j.prompt_version, j.lease_token, j.attempt_count, j.requested_at,
			       ko.title AS title_ko, en.title AS title_en,
			       COALESCE(ko.address_text, p.road_address, p.address) AS address,
			       ko.overview AS overview_ko
			FROM place_editorial_jobs j
			JOIN places p ON p.id = j.place_id
			JOIN place_localizations ko ON ko.place_id = p.id AND ko.language = 'KO'
			LEFT JOIN place_localizations en ON en.place_id = p.id AND en.language = 'EN'
			  AND en.translation_source IN ('KTO_EN', 'MANUAL_EDITED')
			WHERE j.id = ? AND j.status = 'PROCESSING'
			""", this::claimedBase, jobId);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		ClaimedBase base = rows.getFirst();
		List<String> styles = jdbcTemplate.queryForList("""
			SELECT travel_style FROM place_style_mappings
			WHERE place_id = ? ORDER BY is_primary DESC, confidence DESC, travel_style
			""", String.class, base.placeId());
		List<String> facts = jdbcTemplate.queryForList("""
			SELECT CONCAT(field_code, ': ', value_text)
			FROM place_detail_attributes
			WHERE place_id = ? AND NULLIF(TRIM(value_text), '') IS NOT NULL
			ORDER BY source_operation, item_sequence, field_code LIMIT 30
			""", String.class, base.placeId());
		GenerationSource source = new GenerationSource(
			base.placeId(), base.titleKo(), base.titleEn(), base.address(),
			base.overviewKo(), styles, facts);
		return Optional.of(new ClaimedJob(
			base.id(), base.publicId(), base.placeId(), base.sourceFingerprint(),
			base.promptVersion(), base.leaseToken(), base.attemptCount(), source,
			base.requestedAt()));
	}

	private LockedJob lockOwnedJob(long jobId, String leaseToken) {
		return jdbcTemplate.query("""
			SELECT id, place_id FROM place_editorial_jobs
			WHERE id = ? AND status = 'PROCESSING' AND lease_token = ?
			FOR UPDATE
			""", (rs, rowNumber) -> new LockedJob(rs.getLong("id"), rs.getLong("place_id")),
			jobId, leaseToken).stream().findFirst()
			.orElseThrow(() -> new IllegalStateException("Editorial job lease is no longer owned"));
	}

	private String currentFingerprint(long placeId) {
		return jdbcTemplate.queryForObject("""
			SELECT
			""" + JdbcEditorialRepository.SOURCE_FINGERPRINT + """
			 AS fingerprint
			FROM places p
			LEFT JOIN place_localizations ko
			  ON ko.place_id = p.id AND ko.language = 'KO'
			LEFT JOIN place_localizations en
			  ON en.place_id = p.id AND en.language = 'EN'
			 AND en.translation_source IN ('KTO_EN', 'MANUAL_EDITED')
			WHERE p.id = ?
			""", String.class, placeId);
	}

	private void markStale(long jobId, long placeId, Instant now) {
		jdbcTemplate.update("""
			UPDATE place_editorial_jobs
			SET status = 'STALE', lease_token = NULL, lease_expires_at = NULL,
			    completed_at = ?, error_code = 'SOURCE_CHANGED',
			    error_message = 'Source changed before editorial completion'
			WHERE id = ?
			""", Timestamp.from(now), jobId);
		jdbcTemplate.update("""
			INSERT INTO place_editorial_audits
			    (place_id, job_id, action, details_json, created_at)
			VALUES (?, ?, 'EDITORIAL_STALE', JSON_OBJECT('errorCode', 'SOURCE_CHANGED'), ?)
			""", placeId, jobId, Timestamp.from(now));
	}

	private void insertLocalization(
		long contentId,
		EditorialLanguage language,
		EditorialGeneration.LocalizedContent content
	) {
		jdbcTemplate.update("""
			INSERT INTO place_editorial_localizations
			    (editorial_content_id, language, topic, one_line_description, short_introduction)
			VALUES (?, ?, ?, ?, ?)
			""", contentId, language.name(), content.topic(),
			content.oneLineDescription(), content.shortIntroduction());
		for (int index = 0; index < content.enjoyPoints().size(); index++) {
			jdbcTemplate.update("""
				INSERT INTO place_editorial_enjoy_points
				    (editorial_content_id, language, display_order, content)
				VALUES (?, ?, ?, ?)
				""", contentId, language.name(), index + 1, content.enjoyPoints().get(index));
		}
	}

	private void upsertAiEnglishLocalization(
		long placeId,
		String titleEn,
		String addressEn,
		String overviewEn,
		Instant generatedAt
	) {
		jdbcTemplate.update("""
			INSERT INTO place_localizations
			    (place_id, language, title, address_text, overview, translation_source,
			     source_hash, created_at, updated_at)
			VALUES (?, 'EN', ?, ?, ?, 'AI_TRANSLATED',
			        SHA2(CONCAT_WS('|', ?, ?, ?), 256), ?, ?)
			ON DUPLICATE KEY UPDATE
			    title = IF(translation_source IN ('KTO_EN', 'MANUAL_EDITED'),
			        title, VALUES(title)),
			    address_text = IF(translation_source IN ('KTO_EN', 'MANUAL_EDITED'),
			        address_text, VALUES(address_text)),
			    overview = IF(translation_source IN ('KTO_EN', 'MANUAL_EDITED'),
			        overview, VALUES(overview)),
			    source_hash = IF(translation_source IN ('KTO_EN', 'MANUAL_EDITED'),
			        source_hash, VALUES(source_hash)),
			    translation_source = IF(translation_source IN ('KTO_EN', 'MANUAL_EDITED'),
			        translation_source, 'AI_TRANSLATED'),
			    updated_at = IF(translation_source IN ('KTO_EN', 'MANUAL_EDITED'),
			        updated_at, VALUES(updated_at))
		""", placeId, EnglishPlaceTitleNormalizer.normalize(titleEn), addressEn, overviewEn,
		EnglishPlaceTitleNormalizer.normalize(titleEn), addressEn, overviewEn,
			Timestamp.from(generatedAt), Timestamp.from(generatedAt));
	}

	private ClaimedBase claimedBase(ResultSet rs, int rowNumber) throws SQLException {
		return new ClaimedBase(
			rs.getLong("id"), rs.getString("public_id"), rs.getLong("place_id"),
			rs.getString("source_fingerprint"), rs.getString("prompt_version"),
			rs.getString("lease_token"), rs.getInt("attempt_count"),
			rs.getString("title_ko"), rs.getString("title_en"),
			rs.getString("address"), rs.getString("overview_ko"),
			rs.getTimestamp("requested_at").toInstant());
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private record LockedJob(long id, long placeId) {
	}

	private record ClaimedBase(
		long id,
		String publicId,
		long placeId,
		String sourceFingerprint,
		String promptVersion,
		String leaseToken,
		int attemptCount,
		String titleKo,
		String titleEn,
		String address,
		String overviewKo,
		Instant requestedAt
	) {
	}
}
