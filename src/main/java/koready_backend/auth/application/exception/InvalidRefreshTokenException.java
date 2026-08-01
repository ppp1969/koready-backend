package koready_backend.auth.application.exception;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("The refresh token is invalid.");
	}
}
