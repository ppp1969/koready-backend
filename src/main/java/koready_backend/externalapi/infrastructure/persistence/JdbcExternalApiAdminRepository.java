package koready_backend.externalapi.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.externalapi.application.port.ExternalApiAdminRepository;
import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.SnapshotRetentionClass;
import koready_backend.externalapi.domain.SnapshotStorageFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcExternalApiAdminRepository implements ExternalApiAdminRepository {

	private static final String CALL_SELECT = """
		SELECT
		    logs.id,
		    logs.provider,
		    logs.api_name,
		    logs.operation,
		    logs.endpoint,
		    logs.request_started_at,
		    logs.response_received_at,
		    logs.duration_ms,
		    logs.success,
		    logs.http_status,
		    logs.request_params_masked,
		    logs.response_summary,
		    logs.external_result_code,
		    logs.item_count,
		    logs.response_bytes,
		    logs.error_message,
		    logs.related_job_id,
		    jobs.job_type AS related_job_type,
		    snapshots.id AS snapshot_id,
		    snapshots.call_log_id AS snapshot_call_log_id,
		    snapshots.provider AS snapshot_provider,
		    snapshots.api_name AS snapshot_api_name,
		    snapshots.operation AS snapshot_operation,
		    snapshots.storage_key AS snapshot_storage_key,
		    snapshots.storage_format AS snapshot_storage_format,
		    snapshots.content_type AS snapshot_content_type,
		    snapshots.raw_content_sha256 AS snapshot_raw_content_sha256,
		    snapshots.stored_object_sha256 AS snapshot_stored_object_sha256,
		    snapshots.byte_size AS snapshot_byte_size,
		    snapshots.compressed_byte_size AS snapshot_compressed_byte_size,
		    snapshots.item_count AS snapshot_item_count,
		    snapshots.captured_at AS snapshot_captured_at,
		    snapshots.retention_class AS snapshot_retention_class,
		    snapshots.retention_until AS snapshot_retention_until,
		    snapshots.immutable AS snapshot_immutable
		FROM open_api_call_logs logs
		LEFT JOIN batch_jobs jobs ON jobs.id = logs.related_job_id
		LEFT JOIN open_api_raw_snapshots snapshots ON snapshots.call_log_id = logs.id
		""";

	private static final String SNAPSHOT_SELECT = """
		SELECT
		    snapshots.id,
		    snapshots.call_log_id,
		    snapshots.provider,
		    snapshots.api_name,
		    snapshots.operation,
		    snapshots.storage_key,
		    snapshots.storage_format,
		    snapshots.content_type,
		    snapshots.raw_content_sha256,
		    snapshots.stored_object_sha256,
		    snapshots.byte_size,
		    snapshots.compressed_byte_size,
		    snapshots.item_count,
		    snapshots.captured_at,
		    snapshots.retention_class,
		    snapshots.retention_until,
		    snapshots.immutable
		FROM open_api_raw_snapshots snapshots
		""";

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	public JdbcExternalApiAdminRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public SummaryAggregate summarize(SummaryCriteria criteria) {
		StringBuilder where = new StringBuilder(
			" WHERE logs.request_started_at >= ? AND logs.request_started_at < ?");
		List<Object> parameters = new ArrayList<>();
		parameters.add(timestamp(criteria.from()));
		parameters.add(timestamp(criteria.to()));
		if (criteria.provider() != null) {
			where.append(" AND logs.provider = ?");
			parameters.add(criteria.provider().name());
		}

		SummaryCounts counts = jdbcTemplate.query(
			"""
			SELECT
			    COUNT(*) AS total_calls,
			    COALESCE(SUM(CASE WHEN logs.success THEN 1 ELSE 0 END), 0) AS success_calls,
			    COALESCE(SUM(CASE WHEN logs.success THEN 0 ELSE 1 END), 0) AS failure_calls,
			    COUNT(snapshots.id) AS raw_snapshot_count
			FROM open_api_call_logs logs
			LEFT JOIN open_api_raw_snapshots snapshots ON snapshots.call_log_id = logs.id
			""" + where,
			(resultSet, rowNumber) -> new SummaryCounts(
				resultSet.getLong("total_calls"),
				resultSet.getLong("success_calls"),
				resultSet.getLong("failure_calls"),
				resultSet.getLong("raw_snapshot_count")),
			parameters.toArray()).getFirst();

		List<ProviderAggregate> providers = jdbcTemplate.query(
			"""
			SELECT
			    logs.provider,
			    COUNT(*) AS calls,
			    COALESCE(SUM(CASE WHEN logs.success THEN 1 ELSE 0 END), 0) AS successes,
			    COALESCE(SUM(CASE WHEN logs.success THEN 0 ELSE 1 END), 0) AS failures,
			    MAX(CASE WHEN logs.success THEN logs.request_started_at END) AS last_success_at
			FROM open_api_call_logs logs
			""" + where + " GROUP BY logs.provider ORDER BY logs.provider ASC",
			(resultSet, rowNumber) -> new ProviderAggregate(
				ExternalApiProvider.valueOf(resultSet.getString("provider")),
				resultSet.getLong("calls"),
				resultSet.getLong("successes"),
				resultSet.getLong("failures"),
				instant(resultSet, "last_success_at")),
			parameters.toArray());

		List<CallRecord> recentFailures = findCallPage(new CallCriteria(
			criteria.provider(),
			null,
			null,
			false,
			null,
			criteria.from(),
			criteria.to(),
			null,
			null,
			null,
			10));
		return new SummaryAggregate(
			counts.totalCalls(),
			counts.successCalls(),
			counts.failureCalls(),
			counts.rawSnapshotCount(),
			providers,
			recentFailures);
	}

	@Override
	public List<CallRecord> findCallPage(CallCriteria criteria) {
		StringBuilder sql = new StringBuilder(CALL_SELECT).append(" WHERE 1 = 1");
		List<Object> parameters = new ArrayList<>();
		appendCallFilters(sql, parameters, criteria);
		sql.append(" ORDER BY logs.id DESC LIMIT ?");
		parameters.add(criteria.limit());
		return jdbcTemplate.query(sql.toString(), this::mapCall, parameters.toArray());
	}

	@Override
	public Optional<CallRecord> findCallById(long callLogId) {
		return jdbcTemplate.query(
			CALL_SELECT + " WHERE logs.id = ?",
			this::mapCall,
			callLogId).stream().findFirst();
	}

	@Override
	public List<SnapshotRecord> findSnapshotPage(SnapshotCriteria criteria) {
		StringBuilder sql = new StringBuilder(SNAPSHOT_SELECT).append(" WHERE 1 = 1");
		List<Object> parameters = new ArrayList<>();
		if (criteria.provider() != null) {
			sql.append(" AND snapshots.provider = ?");
			parameters.add(criteria.provider().name());
		}
		if (criteria.operation() != null) {
			sql.append(" AND snapshots.operation = ?");
			parameters.add(criteria.operation());
		}
		if (criteria.retentionClass() != null) {
			sql.append(" AND snapshots.retention_class = ?");
			parameters.add(criteria.retentionClass().name());
		}
		if (criteria.from() != null) {
			sql.append(" AND snapshots.captured_at >= ?");
			parameters.add(timestamp(criteria.from()));
		}
		if (criteria.to() != null) {
			sql.append(" AND snapshots.captured_at < ?");
			parameters.add(timestamp(criteria.to()));
		}
		if (criteria.beforeId() != null) {
			sql.append(" AND snapshots.id < ?");
			parameters.add(criteria.beforeId());
		}
		sql.append(" ORDER BY snapshots.id DESC LIMIT ?");
		parameters.add(criteria.limit());
		return jdbcTemplate.query(
			sql.toString(),
			JdbcExternalApiAdminRepository::mapSnapshot,
			parameters.toArray());
	}

	@Override
	public Optional<SnapshotRecord> findSnapshotById(long snapshotId) {
		return jdbcTemplate.query(
			SNAPSHOT_SELECT + " WHERE snapshots.id = ?",
			JdbcExternalApiAdminRepository::mapSnapshot,
			snapshotId).stream().findFirst();
	}

	private static void appendCallFilters(
		StringBuilder sql,
		List<Object> parameters,
		CallCriteria criteria
	) {
		if (criteria.provider() != null) {
			sql.append(" AND logs.provider = ?");
			parameters.add(criteria.provider().name());
		}
		if (criteria.apiName() != null) {
			sql.append(" AND logs.api_name = ?");
			parameters.add(criteria.apiName());
		}
		if (criteria.operation() != null) {
			sql.append(" AND logs.operation = ?");
			parameters.add(criteria.operation());
		}
		if (criteria.success() != null) {
			sql.append(" AND logs.success = ?");
			parameters.add(criteria.success());
		}
		if (criteria.httpStatus() != null) {
			sql.append(" AND logs.http_status = ?");
			parameters.add(criteria.httpStatus());
		}
		if (criteria.from() != null) {
			sql.append(" AND logs.request_started_at >= ?");
			parameters.add(timestamp(criteria.from()));
		}
		if (criteria.to() != null) {
			sql.append(" AND logs.request_started_at < ?");
			parameters.add(timestamp(criteria.to()));
		}
		if (criteria.relatedJobId() != null) {
			sql.append(" AND logs.related_job_id = ?");
			parameters.add(criteria.relatedJobId());
		}
		if (criteria.hasRawSnapshot() != null) {
			sql.append(criteria.hasRawSnapshot()
				? " AND snapshots.id IS NOT NULL"
				: " AND snapshots.id IS NULL");
		}
		if (criteria.beforeId() != null) {
			sql.append(" AND logs.id < ?");
			parameters.add(criteria.beforeId());
		}
	}

	private CallRecord mapCall(ResultSet resultSet, int rowNumber) throws SQLException {
		return new CallRecord(
			resultSet.getLong("id"),
			ExternalApiProvider.valueOf(resultSet.getString("provider")),
			resultSet.getString("api_name"),
			resultSet.getString("operation"),
			resultSet.getString("endpoint"),
			instant(resultSet, "request_started_at"),
			instant(resultSet, "response_received_at"),
			nullableLong(resultSet, "duration_ms"),
			resultSet.getBoolean("success"),
			nullableInteger(resultSet, "http_status"),
			jsonMap(resultSet.getString("request_params_masked")),
			jsonMap(resultSet.getString("response_summary")),
			resultSet.getString("external_result_code"),
			nullableInteger(resultSet, "item_count"),
			nullableLong(resultSet, "response_bytes"),
			resultSet.getString("error_message"),
			nullableLong(resultSet, "related_job_id"),
			resultSet.getString("related_job_type"),
			mapJoinedSnapshot(resultSet));
	}

	private SnapshotRecord mapJoinedSnapshot(ResultSet resultSet) throws SQLException {
		Long snapshotId = nullableLong(resultSet, "snapshot_id");
		if (snapshotId == null) {
			return null;
		}
		return new SnapshotRecord(
			snapshotId,
			resultSet.getLong("snapshot_call_log_id"),
			ExternalApiProvider.valueOf(resultSet.getString("snapshot_provider")),
			resultSet.getString("snapshot_api_name"),
			resultSet.getString("snapshot_operation"),
			resultSet.getString("snapshot_storage_key"),
			SnapshotStorageFormat.valueOf(resultSet.getString("snapshot_storage_format")),
			resultSet.getString("snapshot_content_type"),
			resultSet.getString("snapshot_raw_content_sha256"),
			resultSet.getString("snapshot_stored_object_sha256"),
			resultSet.getLong("snapshot_byte_size"),
			resultSet.getLong("snapshot_compressed_byte_size"),
			resultSet.getInt("snapshot_item_count"),
			instant(resultSet, "snapshot_captured_at"),
			SnapshotRetentionClass.valueOf(
				resultSet.getString("snapshot_retention_class")),
			instant(resultSet, "snapshot_retention_until"),
			resultSet.getBoolean("snapshot_immutable"));
	}

	private static SnapshotRecord mapSnapshot(ResultSet resultSet, int rowNumber)
		throws SQLException {
		return new SnapshotRecord(
			resultSet.getLong("id"),
			resultSet.getLong("call_log_id"),
			ExternalApiProvider.valueOf(resultSet.getString("provider")),
			resultSet.getString("api_name"),
			resultSet.getString("operation"),
			resultSet.getString("storage_key"),
			SnapshotStorageFormat.valueOf(resultSet.getString("storage_format")),
			resultSet.getString("content_type"),
			resultSet.getString("raw_content_sha256"),
			resultSet.getString("stored_object_sha256"),
			resultSet.getLong("byte_size"),
			resultSet.getLong("compressed_byte_size"),
			resultSet.getInt("item_count"),
			instant(resultSet, "captured_at"),
			SnapshotRetentionClass.valueOf(resultSet.getString("retention_class")),
			instant(resultSet, "retention_until"),
			resultSet.getBoolean("immutable"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> jsonMap(String value) {
		if (value == null || value.isBlank()) {
			return Map.of();
		}
		try {
			return jsonMapper.readValue(value, Map.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("External API metadata could not be parsed", exception);
		}
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	private static Integer nullableInteger(ResultSet resultSet, String column)
		throws SQLException {
		int value = resultSet.getInt(column);
		return resultSet.wasNull() ? null : value;
	}

	private record SummaryCounts(
		long totalCalls,
		long successCalls,
		long failureCalls,
		long rawSnapshotCount
	) {
	}
}
