package koready_backend.buddy.application.exception;

public class MessageIdempotencyConflictException extends RuntimeException {

	public MessageIdempotencyConflictException() {
		super("The Idempotency-Key was already used for another message request.");
	}
}
