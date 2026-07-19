package koready_backend.batch.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.batch.application.port.BatchJobExecutionRepository;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcBatchJobExecutionRepository implements BatchJobExecutionRepository {

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	public JdbcBatchJobExecutionRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	@Transactional
	public Optional<ClaimedJob> claimNextQueued() {
		return jdbcTemplate.query("""
			SELECT jobs.id, jobs.job_type, jobs.parameters_json, items.id AS item_id
			FROM batch_jobs jobs
			JOIN batch_job_items items ON items.batch_job_id = jobs.id
			WHERE jobs.status = 'PENDING' AND items.status = 'PENDING'
			ORDER BY jobs.id ASC
			LIMIT 1 FOR UPDATE
			""", this::mapClaimed).stream().findFirst().map(job -> {
			jdbcTemplate.update("""
				UPDATE batch_jobs SET status = 'RUNNING', started_at = UTC_TIMESTAMP(6)
				WHERE id = ? AND status = 'PENDING'
				""", job.id());
			jdbcTemplate.update("UPDATE batch_job_items SET status = 'RUNNING' WHERE id = ?", job.itemId());
			return job;
		});
	}

	@Override
	public void recoverInterruptedJobs(java.time.Instant recoveredAt) {
		jdbcTemplate.update("""
			UPDATE batch_jobs
			SET status = 'FAILED', finished_at = ?, message = 'Batch job interrupted by a restart.',
				active_execution_slot = NULL
			WHERE status = 'RUNNING'
			""", Timestamp.from(recoveredAt));
		jdbcTemplate.update("""
			UPDATE batch_job_items items
			JOIN batch_jobs jobs ON jobs.id = items.batch_job_id
			SET items.status = 'FAILED', items.error_message = 'Batch item failed.'
			WHERE jobs.status = 'FAILED' AND items.status = 'RUNNING'
			""");
	}

	@Override
	public void complete(ClaimedJob job, Completion completion) {
		BatchJobStatus status = completion.failureCount() == 0
			? BatchJobStatus.COMPLETED
			: BatchJobStatus.PARTIAL_FAILED;
		jdbcTemplate.update("""
			UPDATE batch_jobs
			SET status = ?, finished_at = ?, processed_count = ?, success_count = ?, failure_count = ?,
				message = NULL, active_execution_slot = NULL
			WHERE id = ? AND status = 'RUNNING'
			""", status.name(), Timestamp.from(completion.finishedAt()), completion.processedCount(),
			completion.successCount(), completion.failureCount(), job.id());
		jdbcTemplate.update("""
			UPDATE batch_job_items SET status = ?, error_message = NULL
			WHERE id = ?
			""", completion.failureCount() == 0 ? "COMPLETED" : "FAILED", job.itemId());
	}

	@Override
	public void fail(ClaimedJob job, java.time.Instant finishedAt) {
		jdbcTemplate.update("""
			UPDATE batch_jobs
			SET status = 'FAILED', finished_at = ?, failure_count = 1, processed_count = 1,
				success_count = 0, message = 'Batch job failed.', active_execution_slot = NULL
			WHERE id = ? AND status = 'RUNNING'
			""", Timestamp.from(finishedAt), job.id());
		jdbcTemplate.update("""
			UPDATE batch_job_items SET status = 'FAILED', error_message = 'Batch item failed.'
			WHERE id = ?
			""", job.itemId());
	}

	private ClaimedJob mapClaimed(ResultSet resultSet, int rowNumber) throws SQLException {
		return new ClaimedJob(
			resultSet.getLong("id"),
			BatchJobType.valueOf(resultSet.getString("job_type")),
			parameters(resultSet.getString("parameters_json")),
			resultSet.getLong("item_id"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parameters(String value) {
		try {
			return value == null ? Map.of() : Map.copyOf(jsonMapper.readValue(value, Map.class));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Batch job parameters could not be parsed", exception);
		}
	}
}
