package koready_backend.location.application.exception;

public final class InvalidLocationSearchTokenException extends RuntimeException {

	public InvalidLocationSearchTokenException() {
		super("Location search token is invalid");
	}
}
