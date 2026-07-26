package koready_backend.kto.application.exception;

public class KtoEnglishReviewCandidateRequiredException extends RuntimeException {

	public KtoEnglishReviewCandidateRequiredException() {
		super("Manual confirmation requires a place from the matcher candidate list.");
	}
}
