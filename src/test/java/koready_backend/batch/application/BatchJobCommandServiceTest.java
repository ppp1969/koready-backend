package koready_backend.batch.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.batch.application.exception.BatchJobRetryNotAllowedException;
import koready_backend.batch.application.port.BatchJobCommandRepository;
import koready_backend.batch.application.port.BatchJobCommandRepository.EnqueueCommand;
import koready_backend.batch.application.port.BatchJobCommandRepository.BatchAuditRecord;
import koready_backend.batch.application.port.BatchJobCommandRepository.RetrySource;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

@ExtendWith(MockitoExtension.class)
class BatchJobCommandServiceTest {

	@Mock
	BatchJobCommandRepository repository;

	@Test
	void acceptsOnlyBoundedKtoParametersAndPersistsAQueuedManualJob() {
		when(repository.enqueue(any())).thenReturn(91L);
		BatchJobCommandService service = service();

		var accepted = service.accept(new BatchJobCommandService.CreateCommand(
			BatchJobType.KTO_FESTIVAL_SYNC,
			Map.of("eventStartDate", "2026-07-01", "startPage", 2, "maxPages", 3),
			"Refresh festival data", "operator-7"));

		ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(91L, accepted.jobId());
		assertEquals(BatchTriggerSource.ADMIN_MANUAL, captor.getValue().triggerSource());
		assertEquals("2026-07-01", captor.getValue().parameters().get("eventStartDate"));
		assertEquals(3, captor.getValue().parameters().get("maxPages"));
		ArgumentCaptor<BatchAuditRecord> auditCaptor = ArgumentCaptor.forClass(BatchAuditRecord.class);
		verify(repository).recordAudit(auditCaptor.capture());
		assertEquals("operator-7", auditCaptor.getValue().actorSubject());
		assertEquals("Refresh festival data", auditCaptor.getValue().reason());
		assertThrows(IllegalArgumentException.class, () -> service.accept(new BatchJobCommandService.CreateCommand(
			BatchJobType.KTO_FESTIVAL_SYNC,
			Map.of("eventStartDate", "not-a-date"),
			"Refresh festival data", "operator-7")));
	}

	@Test
	void retriesOnlyFailedJobsWithANewLinkedJob() {
		when(repository.findRetrySourceForUpdate(7L)).thenReturn(Optional.of(new RetrySource(
			7L, BatchJobType.KTO_DAILY_SYNC, BatchJobStatus.FAILED, Map.of("startPage", 1, "maxPages", 1))));
		when(repository.enqueue(any())).thenReturn(92L);
		BatchJobCommandService service = service();

		var accepted = service.retry(
			7L, new BatchJobCommandService.RetryCommand("FAILED_ITEMS", "Retry once", "operator-7"));

		assertEquals(7L, accepted.originalJobId());
		assertEquals(BatchTriggerSource.RETRY, accepted.triggerSource());
		ArgumentCaptor<BatchAuditRecord> auditCaptor = ArgumentCaptor.forClass(BatchAuditRecord.class);
		verify(repository).recordAudit(auditCaptor.capture());
		assertEquals("BATCH_JOB_RETRY_ACCEPTED", auditCaptor.getValue().action());
		assertEquals(92L, auditCaptor.getValue().jobId());
		when(repository.findRetrySourceForUpdate(8L)).thenReturn(Optional.of(new RetrySource(
			8L, BatchJobType.KTO_DAILY_SYNC, BatchJobStatus.COMPLETED, Map.of())));
		assertThrows(BatchJobRetryNotAllowedException.class, () -> service.retry(
			8L, new BatchJobCommandService.RetryCommand("FAILED_ITEMS", "Retry once", "operator-7")));
	}

	private BatchJobCommandService service() {
		return new BatchJobCommandService(repository, Clock.fixed(
			Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));
	}
}
