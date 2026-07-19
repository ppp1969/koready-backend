package koready_backend.evidence.application.exception;

public final class EvidenceBundleNotFoundException extends RuntimeException {

	public EvidenceBundleNotFoundException() {
		super("Evidence bundle was not found.");
	}
}
