package koready_backend.kto.application.exception;

public class KtoPhotoAwardMappingConflictException extends RuntimeException {

	public KtoPhotoAwardMappingConflictException() {
		super("The requested photo award display order is already in use.");
	}
}
