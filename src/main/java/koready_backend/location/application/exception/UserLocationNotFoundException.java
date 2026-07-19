package koready_backend.location.application.exception;

public final class UserLocationNotFoundException extends RuntimeException {

	public UserLocationNotFoundException(long locationId) {
		super("User location was not found: " + locationId);
	}
}
