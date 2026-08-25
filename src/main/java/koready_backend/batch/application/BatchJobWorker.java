package koready_backend.batch.application;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import koready_backend.batch.application.port.BatchJobExecutionRepository;
import koready_backend.batch.application.port.BatchJobExecutionRepository.Completion;
import koready_backend.batch.application.port.KtoBatchJobRunner;
import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoDataConsistencyException;

@Component
@ConditionalOnProperty(
	prefix = "koready.kto.manual-batch.worker",
	name = "enabled",
	havingValue = "true",
	matchIfMissing = true)
public class BatchJobWorker {
	private static final Logger log = LoggerFactory.getLogger(BatchJobWorker.class);

	private final BatchJobExecutionRepository repository;
	private final KtoBatchJobRunner runner;
	private final Clock clock;

	@Autowired
	public BatchJobWorker(BatchJobExecutionRepository repository, KtoBatchJobRunner runner) {
		this(repository, runner, Clock.systemUTC());
	}

	BatchJobWorker(BatchJobExecutionRepository repository, KtoBatchJobRunner runner, Clock clock) {
		this.repository = repository;
		this.runner = runner;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${koready.kto.manual-batch.worker.poll-delay:PT30S}")
	public void processNext() {
		repository.claimNextQueued().ifPresent(job -> {
			try {
				var result = runner.run(job);
				repository.complete(job, new Completion(
					result.processedCount(), result.successCount(), result.failureCount(), Instant.now(clock),
					result.continuation()));
			} catch (RuntimeException exception) {
				log.error(
					"Batch job failed. jobId={}, jobType={}, exceptionType={}",
					job.id(), job.jobType(), exception.getClass().getSimpleName());
				repository.fail(job, Instant.now(clock), failureCode(exception));
			}
		});
	}

	private static String failureCode(RuntimeException exception) {
		if (exception instanceof KtoDataConsistencyException) {
			return "KTO_DATA_CONSISTENCY_ERROR";
		}
		if (exception instanceof KtoProviderException providerException
			&& ("22".equals(providerException.providerCode())
				|| "HTTP_429".equals(providerException.providerCode()))) {
			return "KTO_QUOTA_EXCEEDED";
		}
		return "BATCH_JOB_FAILED";
	}

	@EventListener(ApplicationReadyEvent.class)
	public void recoverInterruptedJobs() {
		repository.recoverInterruptedJobs(Instant.now(clock));
	}
}
