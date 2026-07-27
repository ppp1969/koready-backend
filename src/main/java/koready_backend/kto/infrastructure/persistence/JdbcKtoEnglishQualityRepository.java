package koready_backend.kto.infrastructure.persistence;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.port.KtoEnglishQualityRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcKtoEnglishQualityRepository
	implements KtoEnglishQualityRepository {

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	public JdbcKtoEnglishQualityRepository(
		JdbcTemplate jdbcTemplate,
		JsonMapper jsonMapper
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public List<QualityTarget> findUnclassified(
		long startAfterSourceRecordId,
		int limit
	) {
		return jdbcTemplate.query(
			"""
			SELECT source.id, source.source_content_id, source.source_hash,
			       snapshot.storage_key
			FROM place_source_records source
			JOIN open_api_raw_snapshots snapshot
			  ON snapshot.id = source.raw_snapshot_id
			WHERE source.provider = 'KTO'
			  AND source.api_name = 'ENG'
			  AND source.language = 'EN'
			  AND source.source_quality IS NULL
			  AND source.id > ?
			  AND NOT EXISTS (
			      SELECT 1
			      FROM place_source_records newer
			      WHERE newer.provider = source.provider
			        AND newer.api_name = source.api_name
			        AND newer.language = source.language
			        AND newer.source_content_id = source.source_content_id
			        AND (
			            newer.captured_at > source.captured_at
			            OR (newer.captured_at = source.captured_at
			                AND newer.id > source.id)
			        )
			  )
			ORDER BY source.id ASC
			LIMIT ?
			""",
			(rs, rowNumber) -> new QualityTarget(
				rs.getLong("id"),
				rs.getString("source_content_id"),
				rs.getString("source_hash"),
				rs.getString("storage_key")),
			startAfterSourceRecordId,
			limit);
	}

	@Override
	@Transactional
	public void classifyAll(List<QualityUpdate> updates) {
		if (updates == null || updates.isEmpty()) {
			throw new IllegalArgumentException(
				"KTO English quality updates are required");
		}
		int[][] counts = jdbcTemplate.batchUpdate(
			"""
			UPDATE place_source_records
			SET source_quality = ?,
			    quality_warnings = CAST(? AS JSON),
			    quality_classified_at = ?,
			    quality_classifier_version = ?
			WHERE id = ?
			  AND source_hash = ?
			  AND source_quality IS NULL
			""",
			updates,
			50,
			(statement, update) -> {
				statement.setString(1, update.quality().name());
				statement.setString(
					2,
					json(update.warnings().stream()
						.map(Enum::name)
						.sorted()
						.toList()));
				statement.setTimestamp(
					3, Timestamp.from(update.classifiedAt()));
				statement.setString(
					4, update.classifierVersion());
				statement.setLong(5, update.sourceRecordId());
				statement.setString(
					6, update.expectedSourceHash());
			});
		boolean conflicted = java.util.Arrays.stream(counts)
			.flatMapToInt(java.util.Arrays::stream)
			.anyMatch(count -> count != 1);
		if (conflicted) {
			throw new IllegalStateException(
				"KTO English source quality update conflicted");
		}
	}

	@Override
	public QualityCoverage summarizeLatest() {
		return jdbcTemplate.queryForObject(
			"""
			WITH ranked_sources AS (
			    SELECT source.source_quality,
			           ROW_NUMBER() OVER (
			               PARTITION BY source.source_content_id
			               ORDER BY source.captured_at DESC, source.id DESC
			           ) AS source_rank
			    FROM place_source_records source
			    WHERE source.provider = 'KTO'
			      AND source.api_name = 'ENG'
			      AND source.language = 'EN'
			)
			SELECT COUNT(*) AS total_count,
			       COALESCE(SUM(source_quality IS NOT NULL), 0) AS classified_count,
			       COALESCE(SUM(source_quality IS NULL), 0) AS pending_count,
			       COALESCE(SUM(source_quality = 'USABLE'), 0) AS usable_count,
			       COALESCE(SUM(source_quality = 'NON_ENGLISH_SUSPECTED'), 0)
			           AS non_english_count,
			       COALESCE(SUM(source_quality = 'ENCODING_SUSPECTED'), 0)
			           AS encoding_count,
			       COALESCE(SUM(source_quality = 'MIXED_OR_UNKNOWN'), 0)
			           AS mixed_count
			FROM ranked_sources
			WHERE source_rank = 1
			""",
			(rs, rowNumber) -> new QualityCoverage(
				rs.getLong("total_count"),
				rs.getLong("classified_count"),
				rs.getLong("pending_count"),
				rs.getLong("usable_count"),
				rs.getLong("non_english_count"),
				rs.getLong("encoding_count"),
				rs.getLong("mixed_count")));
	}

	private String json(Object value) {
		try {
			return jsonMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException(
				"KTO English source quality warnings could not be serialized",
				exception);
		}
	}
}
