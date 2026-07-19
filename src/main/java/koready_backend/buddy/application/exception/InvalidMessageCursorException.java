package koready_backend.buddy.application.exception;

public class InvalidMessageCursorException extends RuntimeException {

	public InvalidMessageCursorException() {
		super("The message cursor is invalid or belongs to another query.");
	}
}
