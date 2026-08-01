package koready_backend.auth.application.exception;

public class InvalidGoogleIdTokenException extends RuntimeException {

	public InvalidGoogleIdTokenException() {
		super("The Google ID token is invalid.");
	}
}
