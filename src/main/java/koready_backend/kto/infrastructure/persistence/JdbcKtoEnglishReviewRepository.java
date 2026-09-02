package koready_backend.kto.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.exception.KtoEnglishReviewCandidateRequiredException;
import koready_backend.kto.application.exception.KtoEnglishReviewConflictException;
import koready_backend.kto.application.exception.KtoEnglishReviewNotFoundException;
import koready_backend.kto.application.port.KtoEnglishReviewRepository;
import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.domain.KtoEnglishReviewDecision;
import koready_backend.kto.domain.KtoEnglishReviewStatus;
import koready_backend.kto.domain.KtoEnglishSourceQuality;
import koready_backend.kto.domain.KtoEnglishSourceQualityWarning;
import koready_backend.place.domain.EnglishPlaceTitleNormalizer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcKtoEnglishReviewRepository implements KtoEnglishReviewRepository {

	private static final String REVIEWABLE_SOURCE_CTE = """
		WITH ranked_sources AS (
		    SELECT source.*,
		           ROW_NUMBER() OVER (
		               PARTITION BY source.source_content_id
		               ORDER BY source.captured_at DESC, source.id DESC
		           ) AS source_rank
		    FROM place_source_records source
		    WHERE source.provider = 'KTO'
		      AND source.api_name = 'ENG'
		      AND source.language = 'EN'
		),
		classified_sources AS (
		    SELECT
		        source.id AS source_record_id,
		        source.source_content_id,
		        source.source_old_content_id,
		        source.source_hash,
		        source.raw_snapshot_id,
		        snapshot.storage_key,
		        source.captured_at,
		        source.source_quality,
		        source.quality_warnings,
		        source.quality_classified_at,
		        COALESCE(
		            decision.status,
		            CASE
		                WHEN EXISTS (
		                    SELECT 1
		                    FROM place_source_matches automatic
		                    WHERE automatic.source_record_id = source.id
		                      AND automatic.status = 'AUTO_CONFIRMED'
		                ) THEN 'AUTO_CONFIRMED'
		                WHEN EXISTS (
		                    SELECT 1
		                    FROM place_source_matches pending
		                    WHERE pending.source_record_id = source.id
		                      AND pending.status IN (
		                          'REVIEW_REQUIRED',
		                          'MANUAL_CONFIRMED',
		                          'REJECTED'
		                      )
		                ) THEN 'REVIEW_REQUIRED'
		                ELSE 'UNMATCHED'
		            END
		        ) AS review_status,
		        (
		            SELECT COUNT(DISTINCT candidate.place_id)
		            FROM place_source_matches candidate
		            WHERE candidate.source_record_id = source.id
		              AND candidate.status <> 'AUTO_CONFIRMED'
		        ) AS candidate_count,
		        COALESCE(decision.version, 0) AS decision_version,
		        decision.selected_place_id,
		        decision.decided_at
		    FROM ranked_sources source
		    JOIN open_api_raw_snapshots snapshot
		      ON snapshot.id = source.raw_snapshot_id
		    LEFT JOIN place_source_review_decisions decision
		      ON decision.source_record_id = source.id
		    WHERE source.source_rank = 1
		)
		""";

	private static final String SUMMARY_COLUMNS = """
		source_record_id, source_content_id, source_old_content_id, source_hash,
		raw_snapshot_id, storage_key, captured_at, review_status, candidate_count,
		source_quality, quality_warnings, quality_classified_at,
		decision_version, selected_place_id, decided_at
		""";

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;
	private final JsonMapper jsonMapper;

	public JdbcKtoEnglishReviewRepository(
		JdbcTemplate jdbcTemplate,
		NamedParameterJdbcTemplate namedJdbcTemplate,
		JsonMapper jsonMapper
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.namedJdbcTemplate = namedJdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public List<ReviewSummaryRecord> findPage(ReviewCriteria criteria) {
		StringBuilder sql = new StringBuilder(REVIEWABLE_SOURCE_CTE)
			.append("SELECT ").append(SUMMARY_COLUMNS)
			.append(" FROM classified_sources source WHERE ");
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("limit", criteria.limit());
		if (criteria.status() == null) {
			sql.append("source.review_status IN ('REVIEW_REQUIRED', 'UNMATCHED')");
		} else {
			sql.append("source.review_status = :status");
			parameters.addValue("status", criteria.status().name());
		}
		if (criteria.quality() != null) {
			sql.append(" AND source.source_quality = :quality");
			parameters.addValue("quality", criteria.quality().name());
		}
		if (criteria.beforeSourceRecordId() != null) {
			sql.append(" AND source.source_record_id < :beforeId");
			parameters.addValue("beforeId", criteria.beforeSourceRecordId());
		}
		if (criteria.search() != null) {
			sql.append("""
				 AND (
				     source.source_content_id LIKE :search ESCAPE '!'
				     OR COALESCE(source.source_old_content_id, '') LIKE :search ESCAPE '!'
				     OR EXISTS (
				         SELECT 1
				         FROM place_source_matches matched
				         JOIN place_localizations korean
				           ON korean.place_id = matched.place_id
				          AND korean.language = 'KO'
				         WHERE matched.source_record_id = source.source_record_id
				           AND korean.title LIKE :search ESCAPE '!'
				     )
				 )
				""");
			parameters.addValue("search", "%" + escapeLike(criteria.search()) + "%");
		}
		sql.append(" ORDER BY source.source_record_id DESC LIMIT :limit");
		return namedJdbcTemplate.query(sql.toString(), parameters, this::summary);
	}

	@Override
	public Optional<ReviewDetailRecord> findBySourceRecordId(long sourceRecordId) {
		List<ReviewSummaryRecord> summaries = jdbcTemplate.query(
			REVIEWABLE_SOURCE_CTE
				+ "SELECT " + SUMMARY_COLUMNS
				+ " FROM classified_sources"
				+ " WHERE source_record_id = ? AND review_status <> 'AUTO_CONFIRMED'",
			this::summary,
			sourceRecordId);
		if (summaries.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new ReviewDetailRecord(
			summaries.getFirst(),
			candidates(sourceRecordId),
			audits(sourceRecordId)));
	}

	@Override
	@Transactional
	public ReviewDecisionRecord review(ReviewCommand command) {
		LockedSource locked = lockSource(command.sourceRecordId());
		if (!locked.sourceContentId().equals(command.source().contentId())
			|| !locked.sourceHash().equals(command.source().sourceHash())) {
			throw new KtoEnglishReviewConflictException();
		}
		if (hasAutomaticMatch(command.sourceRecordId())) {
			throw new KtoEnglishReviewNotFoundException(command.sourceRecordId());
		}
		CurrentDecision current = currentDecision(command.sourceRecordId());
		int currentVersion = current == null ? 0 : current.version();
		if (command.expectedVersion() != currentVersion) {
			throw new KtoEnglishReviewConflictException();
		}
		if (command.decision() == KtoEnglishReviewDecision.MANUAL_CONFIRMED
			&& !isCandidate(command.sourceRecordId(), command.selectedPlaceId())) {
			throw new KtoEnglishReviewCandidateRequiredException();
		}
		if (command.decision() == KtoEnglishReviewDecision.MANUAL_CONFIRMED
			&& (command.source().title() == null || command.source().title().isBlank())) {
			throw new KtoEnglishReviewCandidateRequiredException();
		}

		int newVersion = currentVersion + 1;
		Long previousPlaceId = current == null ? null : current.selectedPlaceId();
		removePreviousLocalization(
			previousPlaceId, command.selectedPlaceId(), locked.sourceContentId());
		updateCandidateStatuses(command);
		if (command.decision() == KtoEnglishReviewDecision.MANUAL_CONFIRMED) {
			upsertLocalization(command.selectedPlaceId(), command.source());
		}
		saveDecision(command, newVersion);
		insertAudit(command, current, newVersion);
		return loadDecision(command.sourceRecordId());
	}

	private LockedSource lockSource(long sourceRecordId) {
		List<LockedSource> sources = jdbcTemplate.query(
			"""
			SELECT source.id, source.source_content_id, source.source_hash
			FROM place_source_records source
			WHERE source.id = ?
			  AND source.provider = 'KTO'
			  AND source.api_name = 'ENG'
			  AND source.language = 'EN'
			  AND NOT EXISTS (
			      SELECT 1
			      FROM place_source_records newer
			      WHERE newer.provider = source.provider
			        AND newer.api_name = source.api_name
			        AND newer.language = source.language
			        AND newer.source_content_id = source.source_content_id
			        AND (
			            newer.captured_at > source.captured_at
			            OR (newer.captured_at = source.captured_at AND newer.id > source.id)
			        )
			  )
			FOR UPDATE
			""",
			(rs, rowNumber) -> new LockedSource(
				rs.getLong("id"),
				rs.getString("source_content_id"),
				rs.getString("source_hash")),
			sourceRecordId);
		if (sources.isEmpty()) {
			throw new KtoEnglishReviewNotFoundException(sourceRecordId);
		}
		return sources.getFirst();
	}

	private boolean hasAutomaticMatch(long sourceRecordId) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT EXISTS (
			    SELECT 1 FROM place_source_matches
			    WHERE source_record_id = ? AND status = 'AUTO_CONFIRMED'
			)
			""",
			Boolean.class,
			sourceRecordId);
	}

	private CurrentDecision currentDecision(long sourceRecordId) {
		return jdbcTemplate.query(
			"""
			SELECT status, selected_place_id, version
			FROM place_source_review_decisions
			WHERE source_record_id = ?
			""",
			(rs, rowNumber) -> new CurrentDecision(
				KtoEnglishReviewStatus.valueOf(rs.getString("status")),
				nullableLong(rs, "selected_place_id"),
				rs.getInt("version")),
			sourceRecordId).stream().findFirst().orElse(null);
	}

	private boolean isCandidate(long sourceRecordId, Long placeId) {
		if (placeId == null) {
			return false;
		}
		return jdbcTemplate.queryForObject(
			"""
			SELECT EXISTS (
			    SELECT 1 FROM place_source_matches
			    WHERE source_record_id = ?
			      AND place_id = ?
			      AND status <> 'AUTO_CONFIRMED'
			)
			""",
			Boolean.class,
			sourceRecordId,
			placeId);
	}

	private void updateCandidateStatuses(ReviewCommand command) {
		if (command.decision() == KtoEnglishReviewDecision.REJECTED) {
			jdbcTemplate.update(
				"""
				UPDATE place_source_matches
				SET status = 'REJECTED', reviewed_at = CURRENT_TIMESTAMP(6)
				WHERE source_record_id = ? AND status <> 'AUTO_CONFIRMED'
				""",
				command.sourceRecordId());
			return;
		}
		jdbcTemplate.update(
			"""
			UPDATE place_source_matches
			SET status = CASE
			        WHEN place_id = ? THEN 'MANUAL_CONFIRMED'
			        ELSE 'REJECTED'
			    END,
			    reviewed_at = CURRENT_TIMESTAMP(6)
			WHERE source_record_id = ? AND status <> 'AUTO_CONFIRMED'
			""",
			command.selectedPlaceId(),
			command.sourceRecordId());
	}

	private void removePreviousLocalization(
		Long previousPlaceId,
		Long selectedPlaceId,
		String sourceContentId
	) {
		if (previousPlaceId == null || previousPlaceId.equals(selectedPlaceId)) {
			return;
		}
		jdbcTemplate.update(
			"""
			DELETE FROM place_localizations
			WHERE place_id = ?
			  AND language = 'EN'
			  AND translation_source = 'KTO_EN'
			  AND source_content_id = ?
			""",
			previousPlaceId,
			sourceContentId);
	}

	private void upsertLocalization(long placeId, KtoEnglishPlaceItem source) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, address_text, translation_source,
			     source_content_id, source_hash)
			VALUES (?, 'EN', ?, ?, 'KTO_EN', ?, ?)
			ON DUPLICATE KEY UPDATE
			    title = IF(
			        translation_source = 'MANUAL_EDITED'
			        OR (translation_source = 'KTO_EN'
			            AND source_content_id <> VALUES(source_content_id)),
			        title, VALUES(title)),
			    address_text = IF(
			        translation_source = 'MANUAL_EDITED'
			        OR (translation_source = 'KTO_EN'
			            AND source_content_id <> VALUES(source_content_id)),
			        address_text, VALUES(address_text)),
			    source_content_id = IF(
			        translation_source = 'MANUAL_EDITED'
			        OR (translation_source = 'KTO_EN'
			            AND source_content_id <> VALUES(source_content_id)),
			        source_content_id, VALUES(source_content_id)),
			    source_hash = IF(
			        translation_source = 'MANUAL_EDITED'
			        OR (translation_source = 'KTO_EN'
			            AND source_content_id <> VALUES(source_content_id)),
			        source_hash, VALUES(source_hash)),
			    translation_source = IF(
			        translation_source = 'MANUAL_EDITED',
			        translation_source, 'KTO_EN')
			""",
			placeId,
			EnglishPlaceTitleNormalizer.normalize(source.title()),
			joinAddress(source.address1(), source.address2()),
			source.contentId(),
			source.sourceHash());
	}

	private void saveDecision(ReviewCommand command, int version) {
		try {
			jdbcTemplate.update(
				"""
				INSERT INTO place_source_review_decisions
				    (source_record_id, status, selected_place_id, reviewed_by,
				     reason, version, decided_at)
				VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
				ON DUPLICATE KEY UPDATE
				    status = VALUES(status),
				    selected_place_id = VALUES(selected_place_id),
				    reviewed_by = VALUES(reviewed_by),
				    reason = VALUES(reason),
				    version = VALUES(version),
				    decided_at = CURRENT_TIMESTAMP(6)
				""",
				command.sourceRecordId(),
				command.decision().name(),
				command.selectedPlaceId(),
				command.reviewedBy(),
				command.reason(),
				version);
		} catch (DuplicateKeyException exception) {
			throw new KtoEnglishReviewConflictException();
		}
	}

	private void insertAudit(
		ReviewCommand command,
		CurrentDecision current,
		int version
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_source_review_audits
			    (source_record_id, previous_status, new_status, previous_place_id,
			     selected_place_id, reviewed_by, reason, decision_version)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			""",
			command.sourceRecordId(),
			current == null ? null : current.status().name(),
			command.decision().name(),
			current == null ? null : current.selectedPlaceId(),
			command.selectedPlaceId(),
			command.reviewedBy(),
			command.reason(),
			version);
	}

	private ReviewDecisionRecord loadDecision(long sourceRecordId) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT source_record_id, status, selected_place_id, version,
			       reviewed_by, reason, decided_at
			FROM place_source_review_decisions
			WHERE source_record_id = ?
			""",
			(rs, rowNumber) -> new ReviewDecisionRecord(
				rs.getLong("source_record_id"),
				KtoEnglishReviewStatus.valueOf(rs.getString("status")),
				nullableLong(rs, "selected_place_id"),
				rs.getInt("version"),
				rs.getString("reviewed_by"),
				rs.getString("reason"),
				instant(rs, "decided_at")),
			sourceRecordId);
	}

	private List<CandidateRecord> candidates(long sourceRecordId) {
		return jdbcTemplate.query(
			"""
			SELECT matched.place_id,
			       COALESCE(korean.title, CONCAT('place-', matched.place_id)) AS title_ko,
			       COALESCE(korean.address_text, place.road_address, place.address) AS address,
			       place.first_image_url,
			       matched.match_method,
			       matched.confidence,
			       matched.candidate_count,
			       COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(
			           matched.evidence_json, '$.imageCandidateCount')) AS UNSIGNED), 0)
			           AS image_candidate_count,
			       COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(
			           matched.evidence_json, '$.coordinateCandidateCount')) AS UNSIGNED), 0)
			           AS coordinate_candidate_count,
			       COALESCE(JSON_EXTRACT(
			           matched.evidence_json, '$.conflict') = TRUE, FALSE)
			           AS evidence_conflict,
			       CASE WHEN decision.selected_place_id = matched.place_id
			           THEN TRUE ELSE FALSE END AS selected
			FROM place_source_matches matched
			JOIN places place ON place.id = matched.place_id
			LEFT JOIN place_localizations korean
			  ON korean.place_id = place.id AND korean.language = 'KO'
			LEFT JOIN place_source_review_decisions decision
			  ON decision.source_record_id = matched.source_record_id
			WHERE matched.source_record_id = ?
			  AND matched.status <> 'AUTO_CONFIRMED'
			ORDER BY matched.confidence DESC, matched.place_id ASC
			""",
			(rs, rowNumber) -> new CandidateRecord(
				rs.getLong("place_id"),
				rs.getString("title_ko"),
				rs.getString("address"),
				rs.getString("first_image_url"),
				rs.getString("match_method"),
				rs.getDouble("confidence"),
				rs.getInt("candidate_count"),
				rs.getInt("image_candidate_count"),
				rs.getInt("coordinate_candidate_count"),
				rs.getBoolean("evidence_conflict"),
				rs.getBoolean("selected")),
			sourceRecordId);
	}

	private List<AuditRecord> audits(long sourceRecordId) {
		return jdbcTemplate.query(
			"""
			SELECT id, previous_status, new_status, previous_place_id,
			       selected_place_id, reviewed_by, reason, decision_version,
			       created_at
			FROM place_source_review_audits
			WHERE source_record_id = ?
			ORDER BY id DESC
			""",
			(rs, rowNumber) -> new AuditRecord(
				rs.getLong("id"),
				nullableStatus(rs.getString("previous_status")),
				KtoEnglishReviewStatus.valueOf(rs.getString("new_status")),
				nullableLong(rs, "previous_place_id"),
				nullableLong(rs, "selected_place_id"),
				rs.getString("reviewed_by"),
				rs.getString("reason"),
				rs.getInt("decision_version"),
				instant(rs, "created_at")),
			sourceRecordId);
	}

	private ReviewSummaryRecord summary(ResultSet rs, int rowNumber) throws SQLException {
		return new ReviewSummaryRecord(
			rs.getLong("source_record_id"),
			rs.getString("source_content_id"),
			rs.getString("source_old_content_id"),
			rs.getString("source_hash"),
			rs.getLong("raw_snapshot_id"),
			rs.getString("storage_key"),
			instant(rs, "captured_at"),
			nullableQuality(rs.getString("source_quality")),
			qualityWarnings(rs.getString("quality_warnings")),
			nullableInstant(rs, "quality_classified_at"),
			KtoEnglishReviewStatus.valueOf(rs.getString("review_status")),
			rs.getInt("candidate_count"),
			rs.getInt("decision_version"),
			nullableLong(rs, "selected_place_id"),
			nullableInstant(rs, "decided_at"));
	}

	private static KtoEnglishSourceQuality nullableQuality(String value) {
		return value == null ? null : KtoEnglishSourceQuality.valueOf(value);
	}

	private Set<KtoEnglishSourceQualityWarning> qualityWarnings(String value) {
		if (value == null) {
			return Set.of();
		}
		try {
			List<String> names = jsonMapper.readValue(
				value,
				new TypeReference<List<String>>() {
				});
			EnumSet<KtoEnglishSourceQualityWarning> warnings =
				EnumSet.noneOf(KtoEnglishSourceQualityWarning.class);
			for (String name : names) {
				warnings.add(KtoEnglishSourceQualityWarning.valueOf(name));
			}
			return Set.copyOf(warnings);
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new IllegalStateException(
				"Stored KTO English quality warnings are invalid",
				exception);
		}
	}

	private static KtoEnglishReviewStatus nullableStatus(String value) {
		return value == null ? null : KtoEnglishReviewStatus.valueOf(value);
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getTimestamp(column).toInstant();
	}

	private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private static String joinAddress(String first, String second) {
		return java.util.stream.Stream.of(first, second)
			.filter(value -> value != null && !value.isBlank())
			.collect(java.util.stream.Collectors.joining(" "));
	}

	private static String escapeLike(String value) {
		return value
			.replace("!", "!!")
			.replace("%", "!%")
			.replace("_", "!_");
	}

	private record LockedSource(long id, String sourceContentId, String sourceHash) {
	}

	private record CurrentDecision(
		KtoEnglishReviewStatus status,
		Long selectedPlaceId,
		int version
	) {
	}
}
