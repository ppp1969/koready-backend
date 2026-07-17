package koready_backend.kto.application.exception;

public final class KtoDuplicateContentIdException extends RuntimeException {

	public KtoDuplicateContentIdException() {
		super("KTO page contains a duplicate content id");
	}
}
