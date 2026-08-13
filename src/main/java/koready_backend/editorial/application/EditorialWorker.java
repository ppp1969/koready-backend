package koready_backend.editorial.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import koready_backend.editorial.application.port.EditorialGenerator;
import koready_backend.editorial.application.port.EditorialWorkerRepository;
import koready_backend.editorial.application.port.EditorialWorkerRepository.ClaimCommand;
import koready_backend.editorial.application.port.EditorialWorkerRepository.CompleteCommand;
import koready_backend.editorial.application.port.EditorialWorkerRepository.FailCommand;

@Component
@ConditionalOnProperty(
	prefix = "koready.editorial.worker",
	name = {"enabled", "runtime-enabled"},
	havingValue = "true"
)
@EnableConfigurationProperties(EditorialWorkerProperties.class)
public class EditorialWorker {

	private static final ZoneId DAILY_LIMIT_ZONE = ZoneId.of("Asia/Seoul");
	private final EditorialWorkerRepository repository;
	private final EditorialGenerator generator;
	private final EditorialOutputValidator validator;
	private final EditorialWorkerProperties properties;
	private final Clock clock;

	public EditorialWorker(
		EditorialWorkerRepository repository,
		EditorialGenerator generator,
		EditorialOutputValidator validator,
		EditorialWorkerProperties properties
	) {
		this(repository, generator, validator, properties, Clock.systemUTC());
	}

	EditorialWorker(
		EditorialWorkerRepository repository,
		EditorialGenerator generator,
		EditorialOutputValidator validator,
		EditorialWorkerProperties properties,
		Clock clock
	) {
		this.repository = repository;
		this.generator = generator;
		this.validator = validator;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${koready.editorial.worker.poll-delay:PT30S}")
	public void poll() {
		processNext();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void recoverOnStartup() {
		repository.recoverExpiredLeases(clock.instant(), properties.maxAttempts());
	}

	public boolean processNext() {
		Instant now = clock.instant();
		LocalDate date = now.atZone(DAILY_LIMIT_ZONE).toLocalDate();
		Instant dayStart = date.atStartOfDay(DAILY_LIMIT_ZONE).toInstant();
		Instant dayEnd = date.plusDays(1).atStartOfDay(DAILY_LIMIT_ZONE).toInstant();
		if (repository.countStartedBetween(dayStart, dayEnd) >= properties.dailyLimit()) {
			return false;
		}
		var claimed = repository.claimNext(new ClaimCommand(
			now, now.plus(properties.leaseDuration()), UUID.randomUUID().toString(),
			properties.maxAttempts()));
		if (claimed.isEmpty()) {
			return false;
		}
		var job = claimed.get();
		try {
			var generation = generator.generate(job.source());
			validator.validate(generation);
			repository.complete(new CompleteCommand(
				job.jobId(), job.leaseToken(), job.sourceFingerprint(),
				job.promptVersion(), generation, clock.instant()));
		} catch (IllegalArgumentException exception) {
			fail(job.jobId(), job.leaseToken(), job.attemptCount(),
				"AI_OUTPUT_INVALID", "AI output failed validation");
		} catch (RuntimeException exception) {
			fail(job.jobId(), job.leaseToken(), job.attemptCount(),
				"AI_GENERATION_FAILED", "AI generation failed; retry policy applied");
		}
		return true;
	}

	private void fail(
		long jobId,
		String leaseToken,
		int attemptCount,
		String errorCode,
		String errorMessage
	) {
		Instant now = clock.instant();
		boolean retry = attemptCount < properties.maxAttempts();
		repository.fail(new FailCommand(
			jobId, leaseToken, retry, errorCode, errorMessage, now,
			retry ? now.plus(properties.retryDelay()) : null));
	}
}
