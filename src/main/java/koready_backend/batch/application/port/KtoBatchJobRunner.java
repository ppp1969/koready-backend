package koready_backend.batch.application.port;

import koready_backend.batch.application.port.BatchJobExecutionRepository.ClaimedJob;

public interface KtoBatchJobRunner {

	RunResult run(ClaimedJob job);

	record RunResult(int processedCount, int successCount, int failureCount) {
		public RunResult {
			if (processedCount < 0 || successCount < 0 || failureCount < 0
				|| successCount + failureCount > processedCount) {
				throw new IllegalArgumentException("Batch job result counts are invalid");
			}
		}
	}
}
