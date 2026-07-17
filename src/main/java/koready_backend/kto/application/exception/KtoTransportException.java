package koready_backend.kto.application.exception;

public final class KtoTransportException extends RuntimeException {

	public KtoTransportException() {
		super("KTO request failed before a valid response was received");
	}
}
