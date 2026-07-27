package koready_backend.batch.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

public interface BatchJobCommandRepository {

	long enqueue(EnqueueCommand command);

	void recordAudit(BatchAuditRecord audit);

	Optional<RetrySource> findRetrySourceForUpdate(long jobId);

	Optional<RetrySource> findLatestSourceForUpdate(BatchJobType jobType);

	record EnqueueCommand(
		BatchJobType jobType,
		BatchTriggerSource triggerSource,
		Long parentJobId,
		Map<String, Object> parameters,
		String scheduleKey,
		Instant createdAt
	) {
		public EnqueueCommand(
			BatchJobType jobType,
			BatchTriggerSource triggerSource,
			Long parentJobId,
			Map<String, Object> parameters,
			Instant createdAt
		) {
			this(
				jobType,
				triggerSource,
				parentJobId,
				parameters,
				null,
				createdAt);
		}
	}

	record RetrySource(long id, BatchJobType jobType, BatchJobStatus status, Map<String, Object> parameters) {
	}

	record BatchAuditRecord(
		String actorSubject,
		String action,
		long jobId,
		String reason,
		Map<String, Object> summary,
		Instant createdAt
	) {
	}
}
