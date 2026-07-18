package koready_backend.onboarding.application.exception;

public final class CandidateSetNotFoundException extends RuntimeException {

	public CandidateSetNotFoundException(String candidateSetId) {
		super("Candidate set was not found: " + candidateSetId);
	}
}
