package koready_backend.kto.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.exception.KtoSnapshotConflictException;
import koready_backend.kto.application.model.KtoRelatedTourRegion;
import koready_backend.kto.application.model.KtoRelatedTourStorePageCommand;
import koready_backend.kto.application.model.KtoRelatedTourStorePageResult;
import koready_backend.kto.application.port.KtoRelatedTourCurationRepository;
import koready_backend.kto.application.port.KtoRelatedTourRegionSource;
import koready_backend.kto.application.port.KtoRelatedTourStore;
import koready_backend.kto.domain.KtoRelatedTourItem;

@Repository
public class KtoRelatedTourJdbcRepository
	implements KtoRelatedTourStore,
	KtoRelatedTourRegionSource,
	KtoRelatedTourCurationRepository {

	private static final String ENDPOINT =
		"https://apis.data.go.kr/B551011/TarRlteTarService1/areaBasedList1";

	private final JdbcTemplate jdbcTemplate;

	public KtoRelatedTourJdbcRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public KtoRelatedTourStorePageResult store(
		KtoRelatedTourStorePageCommand command
	) {
		ExistingSnapshot existing =
			findSnapshot(command.snapshot().storageKey());
		if (existing != null) {
			if (!existing.rawHash().equals(
					command.page().responseSha256())
				|| existing.itemCount()
					!= command.page().items().size()) {
				throw new KtoSnapshotConflictException();
			}
			return new KtoRelatedTourStorePageResult(
				existing.itemCount(), true);
		}

		long callLogId = insertCallLog(command);
		long snapshotId = insertSnapshot(command, callLogId);
		upsertRecords(command, snapshotId);
		refreshAutoMappings(command.page().items());
		return new KtoRelatedTourStorePageResult(
			command.page().items().size(), false);
	}

	@Override
	public List<KtoRelatedTourRegion> findAfter(
		String startAfterRegionKey,
		int limit
	) {
		if (startAfterRegionKey == null || limit < 1 || limit > 11) {
			throw new IllegalArgumentException(
				"Related tour region query is invalid");
		}
		return jdbcTemplate.query(
			"""
			SELECT area_code, signgu_code
			FROM (
			    SELECT DISTINCT
			        CASE
			            WHEN CHAR_LENGTH(ldong_regn_cd) > 2
			                THEN LEFT(ldong_regn_cd, 2)
			            ELSE ldong_regn_cd
			        END AS area_code,
			        CASE
			            WHEN CHAR_LENGTH(ldong_signgu_cd) >= 5
			                THEN LEFT(ldong_signgu_cd, 5)
			            ELSE CONCAT(
			                CASE
			                    WHEN CHAR_LENGTH(ldong_regn_cd) > 2
			                        THEN LEFT(ldong_regn_cd, 2)
			                    ELSE ldong_regn_cd
			                END,
			                LPAD(ldong_signgu_cd, 3, '0'))
			        END AS signgu_code
			    FROM places
			    WHERE active = TRUE
			      AND show_flag = TRUE
			      AND ldong_regn_cd REGEXP '^[0-9]+$'
			      AND ldong_signgu_cd REGEXP '^[0-9]+$'
			) region
			WHERE CONCAT(area_code, ':', signgu_code) > ?
			ORDER BY area_code ASC, signgu_code ASC
			LIMIT ?
			""",
			(resultSet, rowNumber) -> new KtoRelatedTourRegion(
				resultSet.getString("area_code"),
				resultSet.getString("signgu_code")),
			startAfterRegionKey,
			limit);
	}

	@Override
	public Optional<RelatedTourRecord> findById(long recordId) {
		return jdbcTemplate.query(
			selectSql() + " WHERE record.id = ?",
			this::mapRecord,
			recordId)
			.stream()
			.findFirst();
	}

	@Override
	public boolean placeExists(long placeId) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM places WHERE id = ?",
			Integer.class,
			placeId);
		return count != null && count == 1;
	}

	@Override
	public void saveMapping(MappingRecord mapping) {
		int updated = jdbcTemplate.update(
			"""
			UPDATE kto_related_tour_mappings
			SET source_place_id = ?,
			    related_place_id = ?,
			    match_status = 'MANUAL_CONFIRMED',
			    match_evidence = JSON_OBJECT(
			        'method', 'OPERATOR_REVIEW',
			        'reason', ?),
			    confirmed_by_subject = ?,
			    confirmation_reason = ?,
			    confirmed_at = ?
			WHERE related_tour_record_id = ?
			""",
			mapping.sourcePlaceId(),
			mapping.relatedPlaceId(),
			mapping.reason(),
			mapping.actorSubject(),
			mapping.reason(),
			Timestamp.from(mapping.confirmedAt()),
			mapping.recordId());
		if (updated == 0) {
			jdbcTemplate.update(
				"""
				INSERT INTO kto_related_tour_mappings
				    (related_tour_record_id, source_place_id,
				     related_place_id, match_status, match_evidence,
				     confirmed_by_subject, confirmation_reason,
				     confirmed_at)
				VALUES (
				    ?, ?, ?, 'MANUAL_CONFIRMED',
				    JSON_OBJECT(
				        'method', 'OPERATOR_REVIEW',
				        'reason', ?),
				    ?, ?, ?)
				""",
				mapping.recordId(),
				mapping.sourcePlaceId(),
				mapping.relatedPlaceId(),
				mapping.reason(),
				mapping.actorSubject(),
				mapping.reason(),
				Timestamp.from(mapping.confirmedAt()));
		}
		upsertRelation(mapping.recordId());
	}

	@Override
	public boolean removeMapping(long recordId) {
		jdbcTemplate.update(
			"DELETE FROM place_relations WHERE related_tour_record_id = ?",
			recordId);
		return jdbcTemplate.update(
			"""
			DELETE FROM kto_related_tour_mappings
			WHERE related_tour_record_id = ?
			""",
			recordId) == 1;
	}

	@Override
	public void recordAudit(AuditRecord audit) {
		jdbcTemplate.update(
			"""
			INSERT INTO admin_audit_logs
			    (actor_subject, action, resource_type, resource_id,
			     reason, created_at)
			VALUES (?, ?, 'KTO_RELATED_TOUR_MAPPING', ?, ?, ?)
			""",
			audit.actorSubject(),
			audit.action(),
			Long.toString(audit.recordId()),
			audit.reason(),
			Timestamp.from(audit.createdAt()));
	}

	@Override
	public List<RelatedTourRecord> findPage(RelatedTourQuery query) {
		StringBuilder sql = new StringBuilder(selectSql())
			.append(" WHERE record.id > ?");
		List<Object> parameters = new ArrayList<>();
		parameters.add(query.startAfterId());
		if (query.query() != null) {
			sql.append("""

				  AND (
				      record.source_name LIKE ?
				      OR record.related_name LIKE ?
				      OR record.area_name LIKE ?
				      OR record.related_region_name LIKE ?
				  )
				""");
			String pattern = "%" + query.query() + "%";
			parameters.add(pattern);
			parameters.add(pattern);
			parameters.add(pattern);
			parameters.add(pattern);
		}
		if (query.matchStatus() != null) {
			if ("UNMATCHED".equals(query.matchStatus())) {
				sql.append(" AND mapping.id IS NULL");
			} else {
				sql.append(" AND mapping.match_status = ?");
				parameters.add(query.matchStatus());
			}
		}
		sql.append(" ORDER BY record.id ASC LIMIT ?");
		parameters.add(query.limit());
		return jdbcTemplate.query(
			sql.toString(),
			this::mapRecord,
			parameters.toArray());
	}

	private ExistingSnapshot findSnapshot(String storageKey) {
		List<ExistingSnapshot> rows = jdbcTemplate.query(
			"""
			SELECT id, raw_content_sha256, item_count
			FROM open_api_raw_snapshots
			WHERE storage_key = ?
			""",
			(resultSet, rowNumber) -> new ExistingSnapshot(
				resultSet.getLong("id"),
				resultSet.getString("raw_content_sha256"),
				resultSet.getInt("item_count")),
			storageKey);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private long insertCallLog(KtoRelatedTourStorePageCommand command) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_call_logs
				    (provider, api_name, operation, endpoint,
				     request_started_at, response_received_at,
				     duration_ms, success, http_status,
				     request_params_masked, response_summary,
				     external_result_code, related_job_id,
				     related_job_item_id, item_count, response_bytes)
				VALUES (
				    'KTO', 'RELATED_TOUR', 'areaBasedList1', ?,
				    ?, ?, ?, TRUE, ?,
				    JSON_OBJECT(
				        'numOfRows', ?, 'pageNo', ?,
				        'baseYm', ?, 'areaCd', ?, 'signguCd', ?,
				        'MobileOS', 'ETC',
				        'MobileApp', 'KoReady',
				        '_type', 'json', 'serviceKey', '***'),
				    JSON_OBJECT(
				        'resultCode', '0000', 'pageNo', ?,
				        'numOfRows', ?, 'totalCount', ?,
				        'responseSha256', ?),
				    '0000', ?, ?, ?, ?)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, ENDPOINT);
			statement.setTimestamp(
				2, Timestamp.from(command.call().requestedAt()));
			statement.setTimestamp(
				3, Timestamp.from(
					command.call().responseReceivedAt()));
			statement.setLong(4, command.call().durationMs());
			statement.setInt(5, command.call().httpStatus());
			statement.setInt(6, command.page().pageSize());
			statement.setInt(7, command.page().pageNumber());
			statement.setString(8, command.baseYearMonth());
			statement.setString(9, command.region().areaCode());
			statement.setString(10, command.region().signguCode());
			statement.setInt(11, command.page().pageNumber());
			statement.setInt(12, command.page().pageSize());
			statement.setInt(13, command.page().totalCount());
			statement.setString(
				14, command.page().responseSha256());
			if (command.batchExecution() == null) {
				statement.setNull(15, java.sql.Types.BIGINT);
				statement.setNull(16, java.sql.Types.BIGINT);
			} else {
				statement.setLong(
					15, command.batchExecution().jobId());
				statement.setLong(
					16, command.batchExecution().jobItemId());
			}
			statement.setInt(
				17, command.page().items().size());
			statement.setLong(18, command.page().responseBytes());
			return statement;
		}, keyHolder);
		return requiredKey(keyHolder);
	}

	private long insertSnapshot(
		KtoRelatedTourStorePageCommand command,
		long callLogId
	) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_raw_snapshots
				    (call_log_id, provider, api_name, operation,
				     storage_key, storage_format, content_type,
				     raw_content_sha256, stored_object_sha256,
				     byte_size, compressed_byte_size, item_count,
				     captured_at, retention_class, immutable)
				VALUES (
				    ?, 'KTO', 'RELATED_TOUR', 'areaBasedList1', ?,
				    'JSON_GZIP', 'application/json', ?, ?, ?, ?, ?,
				    ?, 'PROVIDER_RESTRICTED', TRUE)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setLong(1, callLogId);
			statement.setString(2, command.snapshot().storageKey());
			statement.setString(
				3, command.page().responseSha256());
			statement.setString(
				4, command.snapshot().storedObjectSha256());
			statement.setLong(5, command.page().responseBytes());
			statement.setLong(
				6, command.snapshot().compressedByteSize());
			statement.setInt(
				7, command.page().items().size());
			statement.setTimestamp(
				8, Timestamp.from(command.snapshot().capturedAt()));
			return statement;
		}, keyHolder);
		return requiredKey(keyHolder);
	}

	private void upsertRecords(
		KtoRelatedTourStorePageCommand command,
		long snapshotId
	) {
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO kto_related_tour_records
			    (base_ym, area_code, area_name,
			     signgu_code, signgu_name,
			     source_tour_code, source_name,
			     related_tour_code, related_name,
			     related_region_code, related_region_name,
			     related_signgu_code, related_signgu_name,
			     category_large, category_medium, category_small,
			     related_rank, source_hash, raw_snapshot_id,
			     source_captured_at)
			VALUES (
			    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
			    ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE
			    area_code = VALUES(area_code),
			    area_name = VALUES(area_name),
			    signgu_code = VALUES(signgu_code),
			    signgu_name = VALUES(signgu_name),
			    source_name = VALUES(source_name),
			    related_name = VALUES(related_name),
			    related_region_code = VALUES(related_region_code),
			    related_region_name = VALUES(related_region_name),
			    related_signgu_code = VALUES(related_signgu_code),
			    related_signgu_name = VALUES(related_signgu_name),
			    category_large = VALUES(category_large),
			    category_medium = VALUES(category_medium),
			    category_small = VALUES(category_small),
			    related_rank = VALUES(related_rank),
			    source_hash = VALUES(source_hash),
			    raw_snapshot_id = VALUES(raw_snapshot_id),
			    source_captured_at = VALUES(source_captured_at)
			""",
			command.page().items(),
			50,
			(statement, item) -> {
				statement.setString(1, item.baseYearMonth());
				statement.setString(2, item.areaCode());
				statement.setString(3, item.areaName());
				statement.setString(4, item.signguCode());
				statement.setString(5, item.signguName());
				statement.setString(6, item.sourceTourCode());
				statement.setString(7, item.sourceName());
				statement.setString(8, item.relatedTourCode());
				statement.setString(9, item.relatedName());
				statement.setString(10, item.relatedRegionCode());
				statement.setString(11, item.relatedRegionName());
				statement.setString(12, item.relatedSignguCode());
				statement.setString(13, item.relatedSignguName());
				statement.setString(14, item.categoryLarge());
				statement.setString(15, item.categoryMedium());
				statement.setString(16, item.categorySmall());
				statement.setInt(17, item.rank());
				statement.setString(18, item.sourceHash());
				statement.setLong(19, snapshotId);
				statement.setTimestamp(
					20, Timestamp.from(
						command.snapshot().capturedAt()));
			});
	}

	private void refreshAutoMappings(
		List<KtoRelatedTourItem> items
	) {
		for (KtoRelatedTourItem item : items) {
			long recordId = jdbcTemplate.queryForObject(
				"""
				SELECT id
				FROM kto_related_tour_records
				WHERE base_ym = ?
				  AND source_tour_code = ?
				  AND related_tour_code = ?
				""",
				Long.class,
				item.baseYearMonth(),
				item.sourceTourCode(),
				item.relatedTourCode());
			String existingStatus = mappingStatus(recordId);
			if ("MANUAL_CONFIRMED".equals(existingStatus)) {
				continue;
			}
			Long sourcePlaceId = uniquePlace(
				item.sourceName(),
				item.areaCode(),
				item.signguCode());
			Long relatedPlaceId = uniquePlace(
				item.relatedName(),
				item.relatedRegionCode(),
				item.relatedSignguCode());
			if (sourcePlaceId != null
				&& relatedPlaceId != null
				&& !sourcePlaceId.equals(relatedPlaceId)) {
				upsertAutoMapping(
					recordId, sourcePlaceId, relatedPlaceId);
				upsertRelation(recordId);
			} else {
				removeAutoMapping(recordId);
			}
		}
	}

	private String mappingStatus(long recordId) {
		List<String> rows = jdbcTemplate.query(
			"""
			SELECT match_status
			FROM kto_related_tour_mappings
			WHERE related_tour_record_id = ?
			""",
			(resultSet, rowNumber) ->
				resultSet.getString("match_status"),
			recordId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private Long uniquePlace(
		String title,
		String regionCode,
		String signguCode
	) {
		StringBuilder sql = new StringBuilder("""
			SELECT place.id
			FROM place_localizations localization
			JOIN places place ON place.id = localization.place_id
			WHERE localization.language = 'KO'
			  AND localization.title = ?
			  AND place.active = TRUE
			  AND place.show_flag = TRUE
			""");
		List<Object> parameters = new ArrayList<>();
		parameters.add(title);
		if (regionCode != null) {
			sql.append("""

				  AND (
				      place.ldong_regn_cd = ?
				      OR LEFT(place.ldong_regn_cd, 2) = ?
				  )
				""");
			parameters.add(regionCode);
			parameters.add(regionCode);
		}
		if (signguCode != null) {
			sql.append("""

				  AND (
				      place.ldong_signgu_cd = ?
				      OR CONCAT(
				          LEFT(place.ldong_regn_cd, 2),
				          LPAD(place.ldong_signgu_cd, 3, '0')) = ?
				  )
				""");
			parameters.add(signguCode);
			parameters.add(signguCode);
		}
		sql.append(" ORDER BY place.id ASC LIMIT 2");
		List<Long> candidates = jdbcTemplate.query(
			sql.toString(),
			(resultSet, rowNumber) -> resultSet.getLong("id"),
			parameters.toArray());
		return candidates.size() == 1 ? candidates.getFirst() : null;
	}

	private void upsertAutoMapping(
		long recordId,
		long sourcePlaceId,
		long relatedPlaceId
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO kto_related_tour_mappings
			    (related_tour_record_id, source_place_id,
			     related_place_id, match_status, match_evidence,
			     confirmed_at)
			VALUES (
			    ?, ?, ?, 'AUTO_CONFIRMED',
			    JSON_OBJECT(
			        'method', 'EXACT_KO_TITLE_AND_REGION',
			        'candidateCount', 1),
			    UTC_TIMESTAMP(6))
			ON DUPLICATE KEY UPDATE
			    source_place_id = VALUES(source_place_id),
			    related_place_id = VALUES(related_place_id),
			    match_status = 'AUTO_CONFIRMED',
			    match_evidence = VALUES(match_evidence),
			    confirmed_by_subject = NULL,
			    confirmation_reason = NULL,
			    confirmed_at = VALUES(confirmed_at)
			""",
			recordId,
			sourcePlaceId,
			relatedPlaceId);
	}

	private void removeAutoMapping(long recordId) {
		if (!"AUTO_CONFIRMED".equals(mappingStatus(recordId))) {
			return;
		}
		jdbcTemplate.update(
			"DELETE FROM place_relations WHERE related_tour_record_id = ?",
			recordId);
		jdbcTemplate.update(
			"""
			DELETE FROM kto_related_tour_mappings
			WHERE related_tour_record_id = ?
			  AND match_status = 'AUTO_CONFIRMED'
			""",
			recordId);
	}

	private void upsertRelation(long recordId) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_relations
			    (related_tour_record_id, source_place_id,
			     related_place_id, relation_source, relation_type,
			     relation_rank, source_base_ym, last_synced_at)
			SELECT
			    record.id,
			    mapping.source_place_id,
			    mapping.related_place_id,
			    'KTO_RELATED',
			    record.category_large,
			    record.related_rank,
			    record.base_ym,
			    record.source_captured_at
			FROM kto_related_tour_records record
			JOIN kto_related_tour_mappings mapping
			    ON mapping.related_tour_record_id = record.id
			WHERE record.id = ?
			ON DUPLICATE KEY UPDATE
			    source_place_id = VALUES(source_place_id),
			    related_place_id = VALUES(related_place_id),
			    relation_type = VALUES(relation_type),
			    relation_rank = VALUES(relation_rank),
			    source_base_ym = VALUES(source_base_ym),
			    last_synced_at = VALUES(last_synced_at)
			""",
			recordId);
	}

	private String selectSql() {
		return """
			SELECT
			    record.id,
			    record.base_ym,
			    record.source_tour_code,
			    record.source_name,
			    record.area_name,
			    record.signgu_name,
			    record.related_tour_code,
			    record.related_name,
			    record.related_region_name,
			    record.related_signgu_name,
			    record.category_large,
			    record.category_medium,
			    record.category_small,
			    record.related_rank,
			    COALESCE(mapping.match_status, 'UNMATCHED')
			        AS match_status,
			    mapping.source_place_id,
			    source_localization.title AS source_place_title,
			    mapping.related_place_id,
			    related_localization.title AS related_place_title,
			    mapping.confirmed_by_subject,
			    mapping.confirmation_reason,
			    mapping.confirmed_at,
			    record.source_captured_at
			FROM kto_related_tour_records record
			LEFT JOIN kto_related_tour_mappings mapping
			    ON mapping.related_tour_record_id = record.id
			LEFT JOIN place_localizations source_localization
			    ON source_localization.place_id = mapping.source_place_id
			   AND source_localization.language = 'KO'
			LEFT JOIN place_localizations related_localization
			    ON related_localization.place_id = mapping.related_place_id
			   AND related_localization.language = 'KO'
			""";
	}

	private RelatedTourRecord mapRecord(
		ResultSet resultSet,
		int rowNumber
	) throws SQLException {
		return new RelatedTourRecord(
			resultSet.getLong("id"),
			resultSet.getString("base_ym"),
			resultSet.getString("source_tour_code"),
			resultSet.getString("source_name"),
			resultSet.getString("area_name"),
			resultSet.getString("signgu_name"),
			resultSet.getString("related_tour_code"),
			resultSet.getString("related_name"),
			resultSet.getString("related_region_name"),
			resultSet.getString("related_signgu_name"),
			resultSet.getString("category_large"),
			resultSet.getString("category_medium"),
			resultSet.getString("category_small"),
			resultSet.getInt("related_rank"),
			resultSet.getString("match_status"),
			nullableLong(resultSet, "source_place_id"),
			resultSet.getString("source_place_title"),
			nullableLong(resultSet, "related_place_id"),
			resultSet.getString("related_place_title"),
			resultSet.getString("confirmed_by_subject"),
			resultSet.getString("confirmation_reason"),
			instant(resultSet, "confirmed_at"),
			instant(resultSet, "source_captured_at"));
	}

	private static long requiredKey(GeneratedKeyHolder keyHolder) {
		Number key = keyHolder.getKey();
		if (key == null || key.longValue() <= 0) {
			throw new IllegalStateException(
				"Related tour database key was not generated");
		}
		return key.longValue();
	}

	private static Long nullableLong(
		ResultSet resultSet,
		String column
	) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	private static java.time.Instant instant(
		ResultSet resultSet,
		String column
	) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private record ExistingSnapshot(
		long snapshotId,
		String rawHash,
		int itemCount
	) {
	}
}
