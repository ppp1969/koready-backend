package koready_backend.kto.application.exception;

public class KtoPhotoAwardNotFoundException extends RuntimeException {

	public KtoPhotoAwardNotFoundException() {
		super("KTO photo award or target place was not found.");
	}
}
