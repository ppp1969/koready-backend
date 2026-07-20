package koready_backend.batch.application.exception;

public final class BatchJobRetryNotAllowedException extends RuntimeException {

	public BatchJobRetryNotAllowedException() {
		super("This batch job cannot be retried.");
	}
}
