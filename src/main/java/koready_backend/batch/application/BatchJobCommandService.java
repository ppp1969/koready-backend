package koready_backend.batch.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.batch.application.exception.BatchJobAlreadyActiveException;
import koready_backend.batch.application.exception.BatchJobNotFoundException;
import koready_backend.batch.application.exception.BatchJobRetryNotAllowedException;
import koready_backend.batch.application.port.BatchJobCommandRepository;
import koready_backend.batch.application.port.BatchJobCommandRepository.BatchAuditRecord;
import koready_backend.batch.application.port.BatchJobCommandRepository.EnqueueCommand;
import koready_backend.batch.application.port.BatchJobCommandRepository.RetrySource;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

@Service
public class BatchJobCommandService {

	private static final Set<BatchJobType> MANUAL_TYPES = Set.of(
		BatchJobType.KTO_DAILY_SYNC, BatchJobType.KTO_FESTIVAL_SYNC);
	private static final int MAX_PAGES = 20;

	private final BatchJobCommandRepository repository;
	private final Clock clock;

	@Autowired
	public BatchJobCommandService(BatchJobCommandRepository repository) {
		this(repository, Clock.systemUTC());
	}

	BatchJobCommandService(BatchJobCommandRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public JobAcceptance accept(CreateCommand command) {
		NormalizedCommand normalized = normalize(command);
		JobAcceptance accepted = enqueue(
			normalized.jobType(), BatchTriggerSource.ADMIN_MANUAL, null, normalized.parameters());
		repository.recordAudit(new BatchAuditRecord(
			normalized.actorSubject(), "BATCH_JOB_ACCEPTED", accepted.jobId(), normalized.reason(),
			normalized.parameters(), Instant.now(clock)));
		return accepted;
	}

	@Transactional
	public JobAcceptance retry(long jobId, RetryCommand command) {
		if (jobId <= 0 || command == null || !"FAILED_ITEMS".equals(command.scope())
			|| blank(command.reason()) || blank(command.actorSubject())) {
			throw new IllegalArgumentException("Batch job retry request is invalid");
		}
		RetrySource source = repository.findRetrySourceForUpdate(jobId)
			.orElseThrow(() -> new BatchJobNotFoundException(jobId));
		if (source.status() != BatchJobStatus.FAILED && source.status() != BatchJobStatus.PARTIAL_FAILED) {
			throw new BatchJobRetryNotAllowedException();
		}
		JobAcceptance accepted = enqueue(source.jobType(), BatchTriggerSource.RETRY, source.id(), source.parameters());
		repository.recordAudit(new BatchAuditRecord(
			command.actorSubject().strip(), "BATCH_JOB_RETRY_ACCEPTED", accepted.jobId(), command.reason().strip(),
			source.parameters(), Instant.now(clock)));
		return accepted;
	}

	private JobAcceptance enqueue(
		BatchJobType jobType, BatchTriggerSource source, Long parentJobId, Map<String, Object> parameters
	) {
		try {
			long jobId = repository.enqueue(new EnqueueCommand(
				jobType, source, parentJobId, parameters, Instant.now(clock)));
			return new JobAcceptance(jobId, jobType, BatchJobStatus.PENDING, source, parentJobId);
		} catch (DuplicateKeyException exception) {
			throw new BatchJobAlreadyActiveException();
		}
	}

	private NormalizedCommand normalize(CreateCommand command) {
		if (command == null || command.jobType() == null || !MANUAL_TYPES.contains(command.jobType())
			|| blank(command.reason()) || blank(command.actorSubject())) {
			throw new IllegalArgumentException("Batch job request is invalid");
		}
		Map<String, Object> input = command.parameters() == null ? Map.of() : command.parameters();
		int startPage = positive(input.get("startPage"), 1, 100_000, "startPage");
		int maxPages = positive(input.get("maxPages"), 1, MAX_PAGES, "maxPages");
		LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
		parameters.put("startPage", startPage);
		parameters.put("maxPages", maxPages);
		if (command.jobType() == BatchJobType.KTO_FESTIVAL_SYNC) {
			parameters.put("eventStartDate", requiredDate(input.get("eventStartDate")));
		}
		return new NormalizedCommand(
			command.jobType(), Map.copyOf(parameters), command.reason().strip(), command.actorSubject().strip());
	}

	private static int positive(Object value, int fallback, int max, String name) {
		if (value == null) {
			return fallback;
		}
		if (!(value instanceof Number number) || number.longValue() < 1 || number.longValue() > max
			|| number.longValue() != number.doubleValue()) {
			throw new IllegalArgumentException("Batch job " + name + " is invalid");
		}
		return number.intValue();
	}

	private static String requiredDate(Object value) {
		if (!(value instanceof String text)) {
			throw new IllegalArgumentException("Festival event start date is required");
		}
		try {
			return LocalDate.parse(text).toString();
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException("Festival event start date is invalid");
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank() || value.strip().length() > 500;
	}

	public record CreateCommand(BatchJobType jobType, Map<String, Object> parameters, String reason, String actorSubject) {
	}

	public record RetryCommand(String scope, String reason, String actorSubject) {
	}

	public record JobAcceptance(
		long jobId, BatchJobType jobType, BatchJobStatus status,
		BatchTriggerSource triggerSource, Long originalJobId
	) {
	}

	private record NormalizedCommand(
		BatchJobType jobType, Map<String, Object> parameters, String reason, String actorSubject
	) {
	}
}
