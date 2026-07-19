package koready_backend.evidence.application.exception;

public final class InvalidEvidenceBundlePeriodException extends RuntimeException {

	public InvalidEvidenceBundlePeriodException() {
		super("Evidence bundle period is invalid.");
	}
}
