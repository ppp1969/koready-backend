package koready_backend.batch.application.exception;

public final class BatchJobNotFoundException extends RuntimeException {

	public BatchJobNotFoundException(long jobId) {
		super("Batch job " + jobId + " was not found.");
	}
}
