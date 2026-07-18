package koready_backend.batch.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import koready_backend.batch.domain.BatchItemStatus;
import koready_backend.batch.domain.BatchItemTargetType;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

public interface BatchJobAdminRepository {

	List<BatchJobRecord> findJobPage(BatchJobCriteria criteria);

	Optional<BatchJobRecord> findJobById(long jobId);

	List<BatchItemRecord> findItemPage(BatchItemCriteria criteria);

	record BatchJobCriteria(
		BatchJobType jobType,
		BatchJobStatus status,
		BatchTriggerSource triggerSource,
		Instant from,
		Instant to,
		Long beforeId,
		int limit
	) {
	}

	record BatchItemCriteria(
		long jobId,
		BatchItemStatus status,
		BatchItemTargetType targetType,
		Long beforeId,
		int limit
	) {
	}

	record BatchJobRecord(
		long id,
		BatchJobType jobType,
		BatchJobStatus status,
		Instant startedAt,
		Instant finishedAt,
		int processedCount,
		int successCount,
		int failureCount,
		String message,
		BatchTriggerSource triggerSource,
		Long triggeredByUserId,
		Long parentJobId,
		Map<String, Object> parameters,
		Instant createdAt,
		Instant updatedAt
	) {
	}

	record BatchItemRecord(
		long id,
		long jobId,
		BatchItemTargetType targetType,
		String targetId,
		BatchItemStatus status,
		String errorMessage,
		Instant createdAt,
		Instant updatedAt
	) {
	}
}
