package koready_backend.batch.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.batch.application.port.BatchJobCommandRepository;
import koready_backend.batch.application.port.BatchJobCommandRepository.EnqueueCommand;
import koready_backend.batch.application.port.BatchJobExecutionRepository;
import koready_backend.batch.application.port.BatchJobExecutionRepository.Completion;
import koready_backend.batch.application.model.BatchJobContinuation;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

@Tag("integration")
@SpringBootTest(properties = "koready.kto.manual-batch.worker.poll-delay=PT1H")
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcBatchJobExecutionRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	BatchJobCommandRepository commandRepository;

	@Autowired
	BatchJobExecutionRepository executionRepository;

	@Test
	void completesOneCatalogWindowAndAtomicallyQueuesTheNextWindow() {
		long firstJobId = commandRepository.enqueue(new EnqueueCommand(
			BatchJobType.KTO_FULL_CATALOG_SYNC,
			BatchTriggerSource.ADMIN_MANUAL,
			null,
			Map.of("startPage", 1, "maxPages", 20),
			Instant.parse("2026-07-22T00:00:00Z")));
		var claimed = executionRepository.claimNextQueued().orElseThrow();

		executionRepository.complete(claimed, new Completion(
			4_000, 4_000, 0, Instant.parse("2026-07-22T00:01:00Z"),
			new BatchJobContinuation(BatchJobType.KTO_FULL_CATALOG_SYNC, Map.of("startPage", 21, "maxPages", 20))));

		var next = executionRepository.claimNextQueued();
		assertTrue(next.isPresent());
		assertEquals(BatchJobType.KTO_FULL_CATALOG_SYNC, next.get().jobType());
		assertEquals(21, next.get().parameters().get("startPage"));
		assertEquals(20, next.get().parameters().get("maxPages"));
		assertTrue(next.get().id() > firstJobId);
	}
}
