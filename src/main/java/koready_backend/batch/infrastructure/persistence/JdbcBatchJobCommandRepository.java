package koready_backend.batch.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import koready_backend.batch.application.port.BatchJobCommandRepository;
import koready_backend.batch.application.port.BatchJobCommandRepository.BatchAuditRecord;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcBatchJobCommandRepository implements BatchJobCommandRepository {

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	public JdbcBatchJobCommandRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public long enqueue(EnqueueCommand command) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
				INSERT INTO batch_jobs
					(job_type, status, trigger_source, parent_job_id, parameters_json, active_execution_slot, created_at)
				VALUES (?, 'PENDING', ?, ?, CAST(? AS JSON), 1, ?)
				""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, command.jobType().name());
			statement.setString(2, command.triggerSource().name());
			statement.setObject(3, command.parentJobId());
			statement.setString(4, json(command.parameters()));
			statement.setTimestamp(5, Timestamp.from(command.createdAt()));
			return statement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null || key.longValue() <= 0) {
			throw new IllegalStateException("Batch job identifier was not generated");
		}
		long id = key.longValue();
		jdbcTemplate.update("""
			INSERT INTO batch_job_items (batch_job_id, target_type, target_id, status)
			VALUES (?, 'API_PAGE', ?, 'PENDING')
			""", id, targetId(command));
		return id;
	}

	private static String targetId(EnqueueCommand command) {
		String operation = command.jobType() == BatchJobType.KTO_FESTIVAL_SYNC
			? "searchFestival2"
			: "areaBasedSyncList2";
		Object startPage = command.parameters().getOrDefault("startPage", 1);
		Object maxPages = command.parameters().getOrDefault("maxPages", 1);
		return operation + ":" + startPage + "+" + maxPages;
	}

	@Override
	public void recordAudit(BatchAuditRecord audit) {
		jdbcTemplate.update("""
			INSERT INTO admin_audit_logs
			    (actor_subject, action, resource_type, resource_id, reason, after_snapshot, created_at)
			VALUES (?, ?, 'BATCH_JOB', ?, ?, CAST(? AS JSON), ?)
			""",
			audit.actorSubject(), audit.action(), Long.toString(audit.jobId()), audit.reason(),
			json(audit.summary()), Timestamp.from(audit.createdAt()));
	}

	@Override
	public Optional<RetrySource> findRetrySourceForUpdate(long jobId) {
		return jdbcTemplate.query("""
			SELECT id, job_type, status, parameters_json
			FROM batch_jobs WHERE id = ? FOR UPDATE
			""", this::mapRetrySource, jobId).stream().findFirst();
	}

	private RetrySource mapRetrySource(ResultSet resultSet, int rowNumber) throws SQLException {
		return new RetrySource(
			resultSet.getLong("id"),
			BatchJobType.valueOf(resultSet.getString("job_type")),
			BatchJobStatus.valueOf(resultSet.getString("status")),
			map(resultSet.getString("parameters_json")));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(String value) {
		if (value == null || value.isBlank()) {
			return Map.of();
		}
		try {
			return Map.copyOf(jsonMapper.readValue(value, Map.class));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Batch job parameters could not be parsed", exception);
		}
	}

	private String json(Map<String, Object> value) {
		try {
			return jsonMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Batch job parameters could not be serialized", exception);
		}
	}
}
