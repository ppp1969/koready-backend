package koready_backend.kto.application.exception;

public class KtoPhotoGalleryMappingConflictException
	extends RuntimeException {

	public KtoPhotoGalleryMappingConflictException() {
		super("The photo gallery display order is already in use.");
	}
}
