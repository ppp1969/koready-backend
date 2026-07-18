package koready_backend.externalapi.application.exception;

public final class InvalidExternalApiCursorException extends RuntimeException {

	public InvalidExternalApiCursorException() {
		super("The external API cursor is invalid or does not match the filters.");
	}
}
