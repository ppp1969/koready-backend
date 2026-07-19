package koready_backend.buddy.application.exception;

public class ReportIdempotencyConflictException extends RuntimeException {

	public ReportIdempotencyConflictException() {
		super("The Idempotency-Key was already used for another report request.");
	}
}
