package koready_backend.batch.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import koready_backend.batch.application.model.BatchJobContinuation;
import koready_backend.batch.domain.BatchJobType;

public interface BatchJobExecutionRepository {

	Optional<ClaimedJob> claimNextQueued();

	void recoverInterruptedJobs(Instant recoveredAt);

	void complete(ClaimedJob job, Completion completion);

	void fail(ClaimedJob job, Instant finishedAt);

	record ClaimedJob(long id, BatchJobType jobType, Map<String, Object> parameters, long itemId) {
	}

	record Completion(int processedCount, int successCount, int failureCount, Instant finishedAt, BatchJobContinuation continuation) {
		public Completion(int processedCount, int successCount, int failureCount, Instant finishedAt) {
			this(processedCount, successCount, failureCount, finishedAt, null);
		}
	}
}
