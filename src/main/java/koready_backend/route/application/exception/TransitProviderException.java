package koready_backend.route.application.exception;

public final class TransitProviderException extends RuntimeException {
	public TransitProviderException() {
		super("Transit provider is unavailable");
	}
}
