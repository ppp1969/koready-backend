package koready_backend.kto.application.model;

public record KtoPhotoGalleryImportRequest(
	int startPage,
	int maxPages
) {

	public KtoPhotoGalleryImportRequest {
		if (startPage < 1 || maxPages < 1 || maxPages > 20) {
			throw new IllegalArgumentException(
				"Photo gallery import window is invalid");
		}
	}
}
