package koready_backend.buddy.application.exception;

public class MessageNotAllowedException extends RuntimeException {

	public MessageNotAllowedException() {
		super("Messaging is not allowed for this profile.");
	}
}
