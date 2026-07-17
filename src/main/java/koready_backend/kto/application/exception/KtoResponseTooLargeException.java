package koready_backend.kto.application.exception;

public final class KtoResponseTooLargeException extends RuntimeException {

	private final int maxResponseBytes;

	public KtoResponseTooLargeException(int maxResponseBytes) {
		super("KTO response exceeded the configured byte limit");
		this.maxResponseBytes = maxResponseBytes;
	}

	public int maxResponseBytes() {
		return maxResponseBytes;
	}
}
