package koready_backend.externalapi.application.exception;

public final class ExternalApiCallNotFoundException extends RuntimeException {

	public ExternalApiCallNotFoundException(long callLogId) {
		super("External API call log " + callLogId + " was not found.");
	}
}
