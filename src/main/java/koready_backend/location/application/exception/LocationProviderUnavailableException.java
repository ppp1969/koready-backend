package koready_backend.location.application.exception;

public final class LocationProviderUnavailableException extends RuntimeException {

	public LocationProviderUnavailableException() {
		super("Location search is temporarily unavailable");
	}
}
