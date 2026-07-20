package koready_backend.batch.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.batch.application.port.BatchJobCommandRepository;
import koready_backend.batch.application.port.BatchJobCommandRepository.BatchAuditRecord;
import koready_backend.batch.application.port.BatchJobCommandRepository.EnqueueCommand;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

@Tag("integration")
@SpringBootTest(properties = "koready.kto.manual-batch.worker.poll-delay=PT1H")
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcBatchJobCommandRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	BatchJobCommandRepository repository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void persistsTheInitialApiPageAndLetsMySqlEnforceTheSingleActiveSlot() {
		long jobId = repository.enqueue(command(BatchJobType.KTO_FESTIVAL_SYNC));

		assertEquals("PENDING", jdbcTemplate.queryForObject(
			"SELECT status FROM batch_jobs WHERE id = ?", String.class, jobId));
		assertEquals("searchFestival2:1+1", jdbcTemplate.queryForObject(
			"SELECT target_id FROM batch_job_items WHERE batch_job_id = ?", String.class, jobId));
		repository.recordAudit(new BatchAuditRecord(
			"operator-7", "BATCH_JOB_ACCEPTED", jobId, "Refresh festival data",
			Map.of("startPage", 1, "maxPages", 1), Instant.parse("2026-07-20T00:00:01Z")));
		assertEquals("BATCH_JOB_ACCEPTED", jdbcTemplate.queryForObject(
			"SELECT action FROM admin_audit_logs WHERE resource_type = 'BATCH_JOB' AND resource_id = ?",
			String.class, Long.toString(jobId)));
		assertThrows(DuplicateKeyException.class, () -> repository.enqueue(command(BatchJobType.KTO_DAILY_SYNC)));
	}

	private static EnqueueCommand command(BatchJobType type) {
		return new EnqueueCommand(
			type,
			BatchTriggerSource.ADMIN_MANUAL,
			null,
			type == BatchJobType.KTO_FESTIVAL_SYNC
				? Map.of("eventStartDate", "2026-07-01", "startPage", 1, "maxPages", 1)
				: Map.of("startPage", 1, "maxPages", 1),
			Instant.parse("2026-07-20T00:00:00Z"));
	}
}
