package koready_backend.place.application.exception;

public class InvalidPlaceCursorException extends RuntimeException {

	public InvalidPlaceCursorException() {
		super("The place cursor is invalid for this request.");
	}
}
