package koready_backend.batch.infrastructure.persistence;

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

import koready_backend.batch.application.port.BatchJobAdminRepository;
import koready_backend.batch.domain.BatchItemStatus;
import koready_backend.batch.domain.BatchItemTargetType;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcBatchJobAdminRepository implements BatchJobAdminRepository {

	private static final String JOB_SELECT = """
		SELECT
		    jobs.id,
		    jobs.job_type,
		    jobs.status,
		    jobs.started_at,
		    jobs.finished_at,
		    jobs.processed_count,
		    jobs.success_count,
		    jobs.failure_count,
		    jobs.message,
		    jobs.trigger_source,
		    jobs.triggered_by_user_id,
		    jobs.parent_job_id,
		    jobs.parameters_json,
		    jobs.created_at,
		    jobs.updated_at
		FROM batch_jobs jobs
		""";

	private static final String ITEM_SELECT = """
		SELECT
		    items.id,
		    items.batch_job_id,
		    items.target_type,
		    items.target_id,
		    items.status,
		    items.error_message,
		    items.created_at,
		    items.updated_at
		FROM batch_job_items items
		""";

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	public JdbcBatchJobAdminRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public List<BatchJobRecord> findJobPage(BatchJobCriteria criteria) {
		StringBuilder sql = new StringBuilder(JOB_SELECT).append(" WHERE 1 = 1");
		List<Object> parameters = new ArrayList<>();
		if (criteria.jobType() != null) {
			sql.append(" AND jobs.job_type = ?");
			parameters.add(criteria.jobType().name());
		}
		if (criteria.status() != null) {
			sql.append(" AND jobs.status = ?");
			parameters.add(criteria.status().name());
		}
		if (criteria.triggerSource() != null) {
			sql.append(" AND jobs.trigger_source = ?");
			parameters.add(criteria.triggerSource().name());
		}
		if (criteria.from() != null) {
			sql.append(" AND jobs.created_at >= ?");
			parameters.add(Timestamp.from(criteria.from()));
		}
		if (criteria.to() != null) {
			sql.append(" AND jobs.created_at < ?");
			parameters.add(Timestamp.from(criteria.to()));
		}
		if (criteria.beforeId() != null) {
			sql.append(" AND jobs.id < ?");
			parameters.add(criteria.beforeId());
		}
		sql.append(" ORDER BY jobs.id DESC LIMIT ?");
		parameters.add(criteria.limit());
		return jdbcTemplate.query(sql.toString(), this::mapJob, parameters.toArray());
	}

	@Override
	public Optional<BatchJobRecord> findJobById(long jobId) {
		return jdbcTemplate.query(
			JOB_SELECT + " WHERE jobs.id = ?",
			this::mapJob,
			jobId).stream().findFirst();
	}

	@Override
	public List<BatchItemRecord> findItemPage(BatchItemCriteria criteria) {
		StringBuilder sql = new StringBuilder(ITEM_SELECT)
			.append(" WHERE items.batch_job_id = ?");
		List<Object> parameters = new ArrayList<>();
		parameters.add(criteria.jobId());
		if (criteria.status() != null) {
			sql.append(" AND items.status = ?");
			parameters.add(criteria.status().name());
		}
		if (criteria.targetType() != null) {
			sql.append(" AND items.target_type = ?");
			parameters.add(criteria.targetType().name());
		}
		if (criteria.beforeId() != null) {
			sql.append(" AND items.id < ?");
			parameters.add(criteria.beforeId());
		}
		sql.append(" ORDER BY items.id DESC LIMIT ?");
		parameters.add(criteria.limit());
		return jdbcTemplate.query(
			sql.toString(),
			JdbcBatchJobAdminRepository::mapItem,
			parameters.toArray());
	}

	private BatchJobRecord mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
		return new BatchJobRecord(
			resultSet.getLong("id"),
			BatchJobType.valueOf(resultSet.getString("job_type")),
			BatchJobStatus.valueOf(resultSet.getString("status")),
			instant(resultSet, "started_at"),
			instant(resultSet, "finished_at"),
			resultSet.getInt("processed_count"),
			resultSet.getInt("success_count"),
			resultSet.getInt("failure_count"),
			resultSet.getString("message"),
			BatchTriggerSource.valueOf(resultSet.getString("trigger_source")),
			nullableLong(resultSet, "triggered_by_user_id"),
			nullableLong(resultSet, "parent_job_id"),
			jsonMap(resultSet.getString("parameters_json")),
			instant(resultSet, "created_at"),
			instant(resultSet, "updated_at"));
	}

	private static BatchItemRecord mapItem(ResultSet resultSet, int rowNumber)
		throws SQLException {
		return new BatchItemRecord(
			resultSet.getLong("id"),
			resultSet.getLong("batch_job_id"),
			BatchItemTargetType.valueOf(resultSet.getString("target_type")),
			resultSet.getString("target_id"),
			BatchItemStatus.valueOf(resultSet.getString("status")),
			resultSet.getString("error_message"),
			instant(resultSet, "created_at"),
			instant(resultSet, "updated_at"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> jsonMap(String value) {
		if (value == null || value.isBlank()) {
			return Map.of();
		}
		try {
			return jsonMapper.readValue(value, Map.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Batch parameters could not be parsed", exception);
		}
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}
}
