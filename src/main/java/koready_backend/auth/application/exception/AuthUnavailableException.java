package koready_backend.auth.application.exception;

public class AuthUnavailableException extends RuntimeException {

	public AuthUnavailableException() {
		super("Authentication is not configured.");
	}
}
