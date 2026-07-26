package koready_backend.kto.application.exception;

public class KtoEnglishReviewNotFoundException extends RuntimeException {

	public KtoEnglishReviewNotFoundException(long sourceRecordId) {
		super("KTO English review source was not found: " + sourceRecordId);
	}
}
