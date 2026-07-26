package koready_backend.kto.application.exception;

public class KtoEnglishReviewSourceUnavailableException extends RuntimeException {

	public KtoEnglishReviewSourceUnavailableException(long sourceRecordId) {
		super("The raw KTO English source is unavailable: " + sourceRecordId);
	}
}
