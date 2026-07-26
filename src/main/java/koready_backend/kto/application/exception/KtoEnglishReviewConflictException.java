package koready_backend.kto.application.exception;

public class KtoEnglishReviewConflictException extends RuntimeException {

	public KtoEnglishReviewConflictException() {
		super("The KTO English review changed. Reload it before deciding again.");
	}
}
