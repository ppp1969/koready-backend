package koready_backend.batch.application.port;

import koready_backend.batch.application.port.BatchJobExecutionRepository.ClaimedJob;
import koready_backend.batch.application.model.BatchJobContinuation;

public interface KtoBatchJobRunner {

	RunResult run(ClaimedJob job);

	record RunResult(int processedCount, int successCount, int failureCount, BatchJobContinuation continuation) {

		public RunResult(int processedCount, int successCount, int failureCount) {
			this(processedCount, successCount, failureCount, null);
		}

		public RunResult {
			if (processedCount < 0 || successCount < 0 || failureCount < 0
				|| successCount + failureCount > processedCount) {
				throw new IllegalArgumentException("Batch job result counts are invalid");
			}
		}
	}

}
