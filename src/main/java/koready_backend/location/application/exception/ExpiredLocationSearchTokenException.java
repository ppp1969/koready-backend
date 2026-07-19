package koready_backend.location.application.exception;

public final class ExpiredLocationSearchTokenException extends RuntimeException {

	public ExpiredLocationSearchTokenException() {
		super("Location search token has expired");
	}
}
