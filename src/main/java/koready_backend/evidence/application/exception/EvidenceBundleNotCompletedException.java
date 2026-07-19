package koready_backend.evidence.application.exception;

public final class EvidenceBundleNotCompletedException extends RuntimeException {

	public EvidenceBundleNotCompletedException() {
		super("Evidence bundle is not completed.");
	}
}
