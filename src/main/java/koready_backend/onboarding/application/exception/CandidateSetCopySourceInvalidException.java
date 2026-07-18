package koready_backend.onboarding.application.exception;

public final class CandidateSetCopySourceInvalidException extends RuntimeException {

	public CandidateSetCopySourceInvalidException() {
		super("Only a published candidate set can be copied");
	}
}
