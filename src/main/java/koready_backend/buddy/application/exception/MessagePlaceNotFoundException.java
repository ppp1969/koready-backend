package koready_backend.buddy.application.exception;

public class MessagePlaceNotFoundException extends RuntimeException {

	public MessagePlaceNotFoundException(long placeId) {
		super("Place not found: " + placeId);
	}
}
