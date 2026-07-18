package koready_backend.place.application.exception;

public class PlaceNotFoundException extends RuntimeException {

	public PlaceNotFoundException(long placeId) {
		super("Place not found: " + placeId);
	}
}
