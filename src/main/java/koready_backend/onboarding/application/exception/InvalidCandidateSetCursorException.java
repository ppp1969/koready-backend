package koready_backend.onboarding.application.exception;

public final class InvalidCandidateSetCursorException extends RuntimeException {

	public InvalidCandidateSetCursorException() {
		super("Candidate set cursor is invalid or belongs to different filters");
	}
}
