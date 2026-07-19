package koready_backend.location.application.exception;

public final class UserLocationUserUnavailableException extends RuntimeException {

	public UserLocationUserUnavailableException() {
		super("Authenticated user is unavailable");
	}
}
