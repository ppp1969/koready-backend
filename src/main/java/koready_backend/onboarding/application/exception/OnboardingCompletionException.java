package koready_backend.onboarding.application.exception;

public class OnboardingCompletionException extends RuntimeException {

	private final Reason reason;

	public OnboardingCompletionException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public enum Reason {
		TRAVEL_STYLES_INVALID,
		SELECTION_INVALID,
		LOCATION_INVALID,
		CANDIDATE_SET_INVALID,
		CANDIDATE_SET_VERSION_MISMATCH,
		PREREQUISITE_INCOMPLETE,
		ALREADY_COMPLETED
	}
}
