package koready_backend.buddy.application.exception;

public final class InvalidMateCursorException extends RuntimeException {

	public InvalidMateCursorException() {
		super("Invalid or mismatched place mate cursor");
	}
}
