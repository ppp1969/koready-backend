package koready_backend.buddy.application.exception;

public class BuddyUserUnavailableException extends RuntimeException {

	public BuddyUserUnavailableException() {
		super("The authenticated user is unavailable.");
	}
}
