package koready_backend.batch.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.batch.application.exception.BatchJobNotFoundException;
import koready_backend.batch.application.exception.InvalidBatchJobCursorException;
import koready_backend.batch.application.exception.InvalidBatchJobPeriodException;
import koready_backend.batch.application.port.BatchJobAdminRepository;
import koready_backend.batch.application.port.BatchJobAdminRepository.BatchItemCriteria;
import koready_backend.batch.application.port.BatchJobAdminRepository.BatchItemRecord;
import koready_backend.batch.application.port.BatchJobAdminRepository.BatchJobCriteria;
import koready_backend.batch.application.port.BatchJobAdminRepository.BatchJobRecord;
import koready_backend.batch.domain.BatchItemStatus;
import koready_backend.batch.domain.BatchItemTargetType;
import koready_backend.batch.domain.BatchJobExposurePolicy;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

@Service
public class BatchJobAdminService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_CURSOR_LENGTH = 512;

	private final BatchJobAdminRepository repository;

	public BatchJobAdminService(BatchJobAdminRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public JobPage listJobs(JobQuery query) {
		validate(query);
		String fingerprint = jobFingerprint(query);
		Long beforeId = decodeCursor(query.cursor(), "JOB", fingerprint);
		List<BatchJobRecord> rows = repository.findJobPage(new BatchJobCriteria(
			query.jobType(),
			query.status(),
			query.triggerSource(),
			query.from(),
			query.to(),
			beforeId,
			query.size() + 1));
		boolean hasMore = rows.size() > query.size();
		List<BatchJobRecord> visible = rows.subList(0, Math.min(query.size(), rows.size()));
		String nextCursor = hasMore && !visible.isEmpty()
			? encodeCursor("JOB", fingerprint, visible.getLast().id())
			: null;
		return new JobPage(
			visible.stream().map(BatchJobAdminService::jobView).toList(),
			nextCursor,
			hasMore);
	}

	@Transactional(readOnly = true)
	public BatchJobView getJob(long jobId) {
		return jobView(loadJob(jobId));
	}

	@Transactional(readOnly = true)
	public ItemPage listItems(long jobId, ItemQuery query) {
		loadJob(jobId);
		validate(query);
		String fingerprint = itemFingerprint(jobId, query);
		Long beforeId = decodeCursor(query.cursor(), "ITEM", fingerprint);
		List<BatchItemRecord> rows = repository.findItemPage(new BatchItemCriteria(
			jobId,
			query.status(),
			query.targetType(),
			beforeId,
			query.size() + 1));
		boolean hasMore = rows.size() > query.size();
		List<BatchItemRecord> visible = rows.subList(0, Math.min(query.size(), rows.size()));
		String nextCursor = hasMore && !visible.isEmpty()
			? encodeCursor("ITEM", fingerprint, visible.getLast().id())
			: null;
		return new ItemPage(
			jobId,
			visible.stream().map(BatchJobAdminService::itemView).toList(),
			nextCursor,
			hasMore);
	}

	private BatchJobRecord loadJob(long jobId) {
		if (jobId <= 0) {
			throw new IllegalArgumentException("Batch job ID must be positive");
		}
		return repository.findJobById(jobId)
			.orElseThrow(() -> new BatchJobNotFoundException(jobId));
	}

	private static BatchJobView jobView(BatchJobRecord row) {
		return new BatchJobView(
			row.id(),
			row.jobType(),
			row.status(),
			row.triggerSource(),
			row.startedAt(),
			row.finishedAt(),
			row.processedCount(),
			row.successCount(),
			row.failureCount(),
			safeJobMessage(row.status()),
			row.triggeredByUserId(),
			row.parentJobId(),
			BatchJobExposurePolicy.safeParameters(row.parameters()),
			row.createdAt(),
			row.updatedAt());
	}

	private static BatchItemView itemView(BatchItemRecord row) {
		return new BatchItemView(
			row.id(),
			row.targetType(),
			row.targetId(),
			row.status(),
			row.status() == BatchItemStatus.FAILED ? "Batch item failed." : null,
			row.createdAt(),
			row.updatedAt());
	}

	private static String safeJobMessage(BatchJobStatus status) {
		return switch (status) {
			case PENDING -> "Batch job is pending.";
			case RUNNING -> "Batch job is running.";
			case COMPLETED -> "Batch job completed.";
			case FAILED -> "Batch job failed.";
			case PARTIAL_FAILED -> "Batch job completed with failures.";
		};
	}

	private static void validate(JobQuery query) {
		if (query == null || query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Invalid batch job page parameters");
		}
		validatePeriod(query.from(), query.to());
	}

	private static void validate(ItemQuery query) {
		if (query == null || query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Invalid batch item page parameters");
		}
	}

	private static void validatePeriod(Instant from, Instant to) {
		if (from != null && to != null && !from.isBefore(to)) {
			throw new InvalidBatchJobPeriodException();
		}
	}

	private static String jobFingerprint(JobQuery query) {
		return fingerprint(
			name(query.jobType()),
			name(query.status()),
			name(query.triggerSource()),
			stringValue(query.from()),
			stringValue(query.to()),
			String.valueOf(query.size()));
	}

	private static String itemFingerprint(long jobId, ItemQuery query) {
		return fingerprint(
			String.valueOf(jobId),
			name(query.status()),
			name(query.targetType()),
			String.valueOf(query.size()));
	}

	private static String encodeCursor(String kind, String fingerprint, long beforeId) {
		String value = "1\t" + kind + "\t" + fingerprint + "\t" + beforeId;
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static Long decodeCursor(String token, String kind, String fingerprint) {
		if (token == null || token.isBlank()) {
			return null;
		}
		if (token.length() > MAX_CURSOR_LENGTH) {
			throw new InvalidBatchJobCursorException();
		}
		try {
			String value = new String(
				Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
			String[] parts = value.split("\t", -1);
			if (parts.length != 4
				|| !"1".equals(parts[0])
				|| !kind.equals(parts[1])
				|| !fingerprint.equals(parts[2])) {
				throw new InvalidBatchJobCursorException();
			}
			long id = Long.parseLong(parts[3]);
			if (id <= 0) {
				throw new InvalidBatchJobCursorException();
			}
			return id;
		} catch (IllegalArgumentException exception) {
			throw new InvalidBatchJobCursorException();
		}
	}

	private static String fingerprint(String... values) {
		String value = String.join("\n", Arrays.stream(values)
			.map(item -> item == null ? "" : item)
			.toList());
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

	private static String stringValue(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	public record JobQuery(
		BatchJobType jobType,
		BatchJobStatus status,
		BatchTriggerSource triggerSource,
		Instant from,
		Instant to,
		String cursor,
		int size
	) {
	}

	public record JobPage(
		List<BatchJobView> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	public record BatchJobView(
		long jobId,
		BatchJobType jobType,
		BatchJobStatus status,
		BatchTriggerSource triggerSource,
		Instant startedAt,
		Instant finishedAt,
		int processedCount,
		int successCount,
		int failureCount,
		String message,
		Long triggeredByUserId,
		Long parentJobId,
		Map<String, Object> parameters,
		Instant createdAt,
		Instant updatedAt
	) {
	}

	public record ItemQuery(
		BatchItemStatus status,
		BatchItemTargetType targetType,
		String cursor,
		int size
	) {
	}

	public record ItemPage(
		long jobId,
		List<BatchItemView> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	public record BatchItemView(
		long itemId,
		BatchItemTargetType targetType,
		String targetId,
		BatchItemStatus status,
		String errorMessage,
		Instant createdAt,
		Instant updatedAt
	) {
	}
}
