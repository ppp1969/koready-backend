package koready_backend.batch.application.exception;

public final class BatchJobAlreadyActiveException extends RuntimeException {

	public BatchJobAlreadyActiveException() {
		super("Another batch job is already queued or running.");
	}
}
