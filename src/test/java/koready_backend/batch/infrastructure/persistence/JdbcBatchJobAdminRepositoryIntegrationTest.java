package koready_backend.batch.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.batch.application.port.BatchJobAdminRepository;
import koready_backend.batch.application.port.BatchJobAdminRepository.BatchItemCriteria;
import koready_backend.batch.application.port.BatchJobAdminRepository.BatchJobCriteria;
import koready_backend.batch.domain.BatchItemStatus;
import koready_backend.batch.domain.BatchItemTargetType;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class JdbcBatchJobAdminRepositoryIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-07-19T11:00:00.123456Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	BatchJobAdminRepository repository;

	private long parentJobId;
	private long jobId;
	private long failedItemId;

	@BeforeEach
	void setUp() {
		parentJobId = insertJob(null, BatchJobStatus.COMPLETED, BatchTriggerSource.SCHEDULED);
		jobId = insertJob(parentJobId, BatchJobStatus.FAILED, BatchTriggerSource.RETRY);
		insertItem(BatchItemStatus.COMPLETED, "searchFestival2:1");
		failedItemId = insertItem(BatchItemStatus.FAILED, "searchFestival2:2");
	}

	@Test
	void filtersJobPagesAndLoadsTheActualDatabaseMetadata() {
		var rows = repository.findJobPage(new BatchJobCriteria(
			BatchJobType.KTO_DAILY_SYNC,
			BatchJobStatus.FAILED,
			BatchTriggerSource.RETRY,
			NOW.minusSeconds(120),
			NOW,
			null,
			10));
		var detail = repository.findJobById(jobId).orElseThrow();

		assertEquals(List.of(jobId), rows.stream().map(
			BatchJobAdminRepository.BatchJobRecord::id).toList());
		assertEquals(parentJobId, detail.parentJobId());
		assertEquals("secret", detail.parameters().get("serviceKey"));
		assertEquals(BatchTriggerSource.RETRY, detail.triggerSource());
		assertTrue(repository.findJobById(999999L).isEmpty());
	}

	@Test
	void filtersItemsUsingOnlyFieldsThatExistInTheSchema() {
		var rows = repository.findItemPage(new BatchItemCriteria(
			jobId,
			BatchItemStatus.FAILED,
			BatchItemTargetType.API_PAGE,
			null,
			10));

		assertEquals(List.of(failedItemId), rows.stream().map(
			BatchJobAdminRepository.BatchItemRecord::id).toList());
		assertEquals("provider secret failure", rows.getFirst().errorMessage());
	}

	private long insertJob(
		Long parentId,
		BatchJobStatus status,
		BatchTriggerSource triggerSource
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO batch_jobs
			    (job_type, status, started_at, finished_at, processed_count,
			     success_count, failure_count, message, trigger_source,
			     triggered_by_user_id, parent_job_id, parameters_json,
			     created_at, updated_at)
			VALUES ('KTO_DAILY_SYNC', ?, ?, ?, 2, 1, 1, 'provider secret message',
			        ?, 42, ?, JSON_OBJECT('serviceKey', 'secret', 'pageSize', 100), ?, ?)
			""",
			status.name(),
			java.sql.Timestamp.from(NOW.minusSeconds(60)),
			java.sql.Timestamp.from(NOW.minusSeconds(30)),
			triggerSource.name(),
			parentId,
			java.sql.Timestamp.from(NOW.minusSeconds(70)),
			java.sql.Timestamp.from(NOW.minusSeconds(30)));
		return jdbcTemplate.queryForObject("SELECT MAX(id) FROM batch_jobs", Long.class);
	}

	private long insertItem(BatchItemStatus status, String targetId) {
		jdbcTemplate.update(
			"""
			INSERT INTO batch_job_items
			    (batch_job_id, target_type, target_id, status, error_message,
			     created_at, updated_at)
			VALUES (?, 'API_PAGE', ?, ?, ?, ?, ?)
			""",
			jobId,
			targetId,
			status.name(),
			status == BatchItemStatus.FAILED ? "provider secret failure" : null,
			java.sql.Timestamp.from(NOW.minusSeconds(50)),
			java.sql.Timestamp.from(NOW.minusSeconds(30)));
		return jdbcTemplate.queryForObject("SELECT MAX(id) FROM batch_job_items", Long.class);
	}
}
