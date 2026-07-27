package koready_backend.kto.application.exception;

public class KtoPhotoGalleryNotFoundException
	extends RuntimeException {

	public KtoPhotoGalleryNotFoundException() {
		super("KTO photo gallery image or place was not found.");
	}
}
