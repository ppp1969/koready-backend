package koready_backend.evidence.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.evidence.application.port.EvidenceBundleContentSource;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.BatchJobRow;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.CallRow;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.DataQualityRow;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.Selection;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.SnapshotRow;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.SyncCursorRow;
import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.SnapshotRetentionClass;
import koready_backend.externalapi.domain.SnapshotStorageFormat;
import koready_backend.externalapi.domain.SyncCursorType;

@Repository
public class JdbcEvidenceBundleContentSource implements EvidenceBundleContentSource {

	private final JdbcTemplate jdbcTemplate;

	public JdbcEvidenceBundleContentSource(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<CallRow> findCalls(Selection selection, long afterId, int limit) {
		StringBuilder sql = new StringBuilder("""
			SELECT logs.id, logs.provider, logs.api_name, logs.operation, logs.request_started_at,
			       logs.response_received_at, logs.duration_ms, logs.success, logs.http_status,
			       logs.external_result_code, logs.item_count, logs.response_bytes,
			       snapshots.id AS snapshot_id, snapshots.storage_key, snapshots.storage_format,
			       snapshots.raw_content_sha256, snapshots.stored_object_sha256, snapshots.byte_size,
			       snapshots.captured_at, snapshots.retention_class, snapshots.retention_until
			FROM open_api_call_logs logs
			LEFT JOIN open_api_raw_snapshots snapshots ON snapshots.call_log_id = logs.id
			WHERE logs.request_started_at >= ? AND logs.request_started_at <= ? AND logs.id > ?
			""");
		List<Object> args = new ArrayList<>(List.of(
			Timestamp.from(selection.from()), Timestamp.from(selection.to()), afterId));
		appendIn(sql, args, "logs.provider", selection.providers().stream().map(Enum::name).toList());
		if (!selection.operations().isEmpty()) {
			appendIn(sql, args, "logs.operation", selection.operations());
		}
		sql.append(" ORDER BY logs.id ASC LIMIT ?");
		args.add(limit);
		return jdbcTemplate.query(sql.toString(), args.toArray(), (resultSet, rowNumber) -> call(resultSet));
	}

	@Override
	public List<BatchJobRow> findBatchJobs(Instant from, Instant to, long afterId, int limit) {
		return jdbcTemplate.query("""
			SELECT id, job_type, status, started_at, finished_at, processed_count, success_count,
			       failure_count, created_at
			FROM batch_jobs
			WHERE created_at >= ? AND created_at <= ? AND id > ?
			ORDER BY id ASC LIMIT ?
			""", (resultSet, rowNumber) -> new BatchJobRow(
				resultSet.getLong("id"), resultSet.getString("job_type"), resultSet.getString("status"),
				instant(resultSet, "started_at"), instant(resultSet, "finished_at"),
				resultSet.getInt("processed_count"), resultSet.getInt("success_count"),
				resultSet.getInt("failure_count"), instant(resultSet, "created_at")),
			Timestamp.from(from), Timestamp.from(to), afterId, limit);
	}

	@Override
	public List<SyncCursorRow> findSyncCursors(Selection selection) {
		StringBuilder sql = new StringBuilder("""
			SELECT id, provider, api_name, operation, cursor_type, cursor_value, enabled,
			       last_success_at, last_failure_at, failure_count, updated_at
			FROM tour_api_sync_cursors WHERE 1 = 1
			""");
		List<Object> args = new ArrayList<>();
		appendIn(sql, args, "provider", selection.providers().stream().map(Enum::name).toList());
		if (!selection.operations().isEmpty()) {
			appendIn(sql, args, "operation", selection.operations());
		}
		sql.append(" ORDER BY id ASC");
		return jdbcTemplate.query(sql.toString(), args.toArray(), (resultSet, rowNumber) -> new SyncCursorRow(
			resultSet.getLong("id"), ExternalApiProvider.valueOf(resultSet.getString("provider")),
			resultSet.getString("api_name"), resultSet.getString("operation"),
			SyncCursorType.valueOf(resultSet.getString("cursor_type")), resultSet.getString("cursor_value"),
			resultSet.getBoolean("enabled"), instant(resultSet, "last_success_at"),
			instant(resultSet, "last_failure_at"), resultSet.getInt("failure_count"), instant(resultSet, "updated_at")));
	}

	@Override
	public DataQualityRow dataQuality() {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) AS total_places,
			       SUM(CASE WHEN active = TRUE AND show_flag = TRUE THEN 1 ELSE 0 END) AS active_places,
			       SUM(CASE WHEN active = TRUE AND show_flag = TRUE
			          AND title_ko IS NOT NULL AND title_ko <> ''
			          AND latitude IS NOT NULL AND longitude IS NOT NULL
			          AND first_image_url IS NOT NULL AND first_image_url <> '' THEN 1 ELSE 0 END) AS curation_ready_places
			FROM places
			""", (resultSet, rowNumber) -> new DataQualityRow(
				resultSet.getLong("total_places"), resultSet.getLong("active_places"),
				resultSet.getLong("curation_ready_places")));
	}

	private void appendIn(StringBuilder sql, List<Object> args, String column, List<String> values) {
		sql.append(" AND ").append(column).append(" IN (");
		sql.append("?,".repeat(values.size()));
		sql.setLength(sql.length() - 1);
		sql.append(')');
		args.addAll(values);
	}

	private CallRow call(ResultSet resultSet) throws SQLException {
		Long snapshotId = nullableLong(resultSet, "snapshot_id");
		SnapshotRow snapshot = snapshotId == null ? null : new SnapshotRow(
			snapshotId, resultSet.getString("storage_key"),
			SnapshotStorageFormat.valueOf(resultSet.getString("storage_format")),
			resultSet.getString("raw_content_sha256"), resultSet.getString("stored_object_sha256"),
			resultSet.getLong("byte_size"), instant(resultSet, "captured_at"),
			SnapshotRetentionClass.valueOf(resultSet.getString("retention_class")),
			instant(resultSet, "retention_until"));
		return new CallRow(resultSet.getLong("id"), ExternalApiProvider.valueOf(resultSet.getString("provider")),
			resultSet.getString("api_name"), resultSet.getString("operation"),
			instant(resultSet, "request_started_at"), instant(resultSet, "response_received_at"),
			nullableLong(resultSet, "duration_ms"), resultSet.getBoolean("success"),
			nullableInteger(resultSet, "http_status"), resultSet.getString("external_result_code"),
			nullableInteger(resultSet, "item_count"), nullableLong(resultSet, "response_bytes"), snapshot);
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
		int value = resultSet.getInt(column);
		return resultSet.wasNull() ? null : value;
	}
}
