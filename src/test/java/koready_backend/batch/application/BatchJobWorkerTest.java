package koready_backend.batch.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

import koready_backend.batch.application.port.BatchJobExecutionRepository;
import koready_backend.batch.application.port.BatchJobExecutionRepository.ClaimedJob;
import koready_backend.batch.application.port.KtoBatchJobRunner;
import koready_backend.batch.application.model.BatchJobContinuation;
import koready_backend.batch.domain.BatchJobType;

@ExtendWith(MockitoExtension.class)
class BatchJobWorkerTest {

	@Mock
	BatchJobExecutionRepository repository;

	@Mock
	KtoBatchJobRunner runner;

	@Test
	void completesTheSingleClaimedJob() {
		ClaimedJob job = job();
		when(repository.claimNextQueued()).thenReturn(Optional.of(job));
		when(runner.run(job)).thenReturn(new KtoBatchJobRunner.RunResult(4, 4, 0));

		worker().processNext();

		verify(repository).complete(any(), any());
		verify(repository, never()).fail(any(), any());
	}

	@Test
	void storesTheNextCatalogRangeOnlyAfterTheCurrentJobCompletes() {
		ClaimedJob job = job();
		when(repository.claimNextQueued()).thenReturn(Optional.of(job));
		when(runner.run(job)).thenReturn(new KtoBatchJobRunner.RunResult(
			4, 4, 0, new BatchJobContinuation(
				BatchJobType.KTO_FULL_CATALOG_SYNC,
				Map.of("startPage", 21, "maxPages", 20))));

		worker().processNext();

		ArgumentCaptor<BatchJobExecutionRepository.Completion> completion = ArgumentCaptor.forClass(
			BatchJobExecutionRepository.Completion.class);
		verify(repository).complete(any(), completion.capture());
		assertEquals(21, completion.getValue().continuation().parameters().get("startPage"));
	}

	@Test
	void marksTheClaimedJobFailedWhenTheRunnerFails() {
		ClaimedJob job = job();
		when(repository.claimNextQueued()).thenReturn(Optional.of(job));
		when(runner.run(job)).thenThrow(new IllegalStateException("provider secret"));

		worker().processNext();

		verify(repository).fail(any(), any());
		verify(repository, never()).complete(any(), any());
	}

	@Test
	void releasesInterruptedRunningJobsWhenTheApplicationBecomesReady() {
		worker().recoverInterruptedJobs();

		verify(repository).recoverInterruptedJobs(any());
	}

	private BatchJobWorker worker() {
		return new BatchJobWorker(repository, runner, Clock.fixed(
			Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));
	}

	private static ClaimedJob job() {
		return new ClaimedJob(1L, BatchJobType.KTO_DAILY_SYNC, Map.of("startPage", 1, "maxPages", 1), 2L);
	}
}
