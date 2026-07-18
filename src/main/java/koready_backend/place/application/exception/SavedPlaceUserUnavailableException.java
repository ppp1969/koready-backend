package koready_backend.place.application.exception;

public class SavedPlaceUserUnavailableException extends RuntimeException {

	public SavedPlaceUserUnavailableException() {
		super("The authenticated user is unavailable.");
	}
}
