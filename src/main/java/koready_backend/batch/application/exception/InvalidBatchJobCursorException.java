package koready_backend.batch.application.exception;

public final class InvalidBatchJobCursorException extends RuntimeException {

	public InvalidBatchJobCursorException() {
		super("The batch cursor is invalid or does not match the filters.");
	}
}
