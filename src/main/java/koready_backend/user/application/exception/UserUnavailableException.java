package koready_backend.user.application.exception;

public class UserUnavailableException extends RuntimeException {

	public UserUnavailableException() {
		super("The authenticated user is unavailable.");
	}
}
