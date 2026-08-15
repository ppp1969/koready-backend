package koready_backend.editorial.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import com.google.genai.errors.ApiException;

import koready_backend.editorial.application.port.EditorialGenerator;
import koready_backend.editorial.application.port.EditorialWorkerRepository;
import koready_backend.editorial.application.port.EditorialWorkerRepository.ClaimCommand;
import koready_backend.editorial.application.port.EditorialWorkerRepository.ClaimedJob;
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

	private static final Logger log = LoggerFactory.getLogger(EditorialWorker.class);
	private static final ZoneId DAILY_LIMIT_ZONE = ZoneId.of("Asia/Seoul");
	private final EditorialWorkerRepository repository;
	private final EditorialGenerator generator;
	private final EditorialOutputValidator validator;
	private final EditorialWorkerProperties properties;
	private final Clock clock;

	@Autowired
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
		try {
			repository.recoverExpiredLeases(clock.instant(), properties.maxAttempts());
		} catch (RuntimeException exception) {
			log.error("Editorial worker lease recovery failed; polling remains isolated");
		}
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
			fail(job, "AI_OUTPUT_INVALID", "AI output failed validation",
				"OUTPUT_VALIDATION", exception);
		} catch (RuntimeException exception) {
			FailureDetails details = failureDetails(exception);
			fail(job, "AI_GENERATION_FAILED", "AI generation failed; retry policy applied",
				details.category(), exception, details.providerHttpStatus());
		}
		return true;
	}

	private void fail(
		ClaimedJob job,
		String errorCode,
		String errorMessage,
		String errorCategory,
		RuntimeException exception
	) {
		fail(job, errorCode, errorMessage, errorCategory, exception, null);
	}

	private void fail(
		ClaimedJob job,
		String errorCode,
		String errorMessage,
		String errorCategory,
		RuntimeException exception,
		Integer providerHttpStatus
	) {
		Instant now = clock.instant();
		boolean retry = job.attemptCount() < properties.maxAttempts();
		Instant nextAttemptAt = retry ? now.plus(properties.retryDelay()) : null;
		repository.fail(new FailCommand(
			job.jobId(), job.leaseToken(), retry, errorCode, errorMessage, now, nextAttemptAt));
		log.error(
			"Editorial AI job failed jobId={} jobPublicId={} placeId={} attempt={} errorCode={} "
				+ "errorCategory={} exceptionType={} providerHttpStatus={} retry={} nextAttemptAt={}",
			job.jobId(), job.publicId(), job.placeId(), job.attemptCount(), errorCode,
			errorCategory, exception.getClass().getSimpleName(), providerHttpStatus,
			retry, nextAttemptAt);
	}

	private static FailureDetails failureDetails(RuntimeException exception) {
		ApiException apiException = findApiException(exception);
		if (apiException != null) {
			int status = apiException.code();
			return new FailureDetails(providerCategory(status), status);
		}
		if (exception instanceof TransientAiException) {
			return new FailureDetails("PROVIDER_TRANSIENT", null);
		}
		if (exception instanceof NonTransientAiException) {
			return new FailureDetails("PROVIDER_NON_TRANSIENT", null);
		}
		return new FailureDetails("UNEXPECTED", null);
	}

	private static ApiException findApiException(Throwable exception) {
		Throwable current = exception;
		for (int depth = 0; current != null && depth < 10; depth++) {
			if (current instanceof ApiException apiException) {
				return apiException;
			}
			if (current.getCause() == current) {
				break;
			}
			current = current.getCause();
		}
		return null;
	}

	private static String providerCategory(int status) {
		return switch (status) {
			case 401, 403 -> "PROVIDER_AUTHORIZATION";
			case 404 -> "PROVIDER_MODEL_OR_ENDPOINT_NOT_FOUND";
			case 429 -> "PROVIDER_RATE_LIMIT";
			default -> status >= 500
				? "PROVIDER_SERVER_ERROR"
				: "PROVIDER_CLIENT_ERROR";
		};
	}

	private record FailureDetails(String category, Integer providerHttpStatus) {
	}
}
