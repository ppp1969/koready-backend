package koready_backend.editorial.application.exception;

public class EditorialPlaceNotFoundException extends RuntimeException {

	public EditorialPlaceNotFoundException(long placeId) {
		super("Editorial place was not found: " + placeId);
	}
}
