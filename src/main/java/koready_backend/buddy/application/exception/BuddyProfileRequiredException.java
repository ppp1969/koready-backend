package koready_backend.buddy.application.exception;

public class BuddyProfileRequiredException extends RuntimeException {

	public BuddyProfileRequiredException() {
		super("Create a Buddy profile before sending messages.");
	}
}
