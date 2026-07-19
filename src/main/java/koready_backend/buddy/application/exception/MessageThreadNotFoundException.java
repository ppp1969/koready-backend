package koready_backend.buddy.application.exception;

public class MessageThreadNotFoundException extends RuntimeException {

	public MessageThreadNotFoundException() {
		super("Message thread not found.");
	}
}
