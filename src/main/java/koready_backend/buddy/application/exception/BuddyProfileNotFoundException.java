package koready_backend.buddy.application.exception;

public final class BuddyProfileNotFoundException extends RuntimeException {

	public BuddyProfileNotFoundException(long profileId) {
		super("Buddy profile not found: " + profileId);
	}
}
