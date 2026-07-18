package koready_backend.onboarding.application.exception;

public final class CandidateSetConcurrentModificationException extends RuntimeException {

	public CandidateSetConcurrentModificationException() {
		super("Candidate set state changed while the request was being processed");
	}
}
