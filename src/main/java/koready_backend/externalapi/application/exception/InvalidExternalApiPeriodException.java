package koready_backend.externalapi.application.exception;

public final class InvalidExternalApiPeriodException extends RuntimeException {

	public InvalidExternalApiPeriodException() {
		super("The external API lookup period is invalid.");
	}
}
