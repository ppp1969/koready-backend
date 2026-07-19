package koready_backend.evidence.application.exception;

public final class EvidenceBundleDownloadUnavailableException extends RuntimeException {

	public EvidenceBundleDownloadUnavailableException() {
		super("Evidence bundle download is unavailable.");
	}
}
