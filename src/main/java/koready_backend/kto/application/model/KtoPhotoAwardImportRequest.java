package koready_backend.kto.application.model;

public record KtoPhotoAwardImportRequest(
	int startPage,
	int maxPages
) {

	public KtoPhotoAwardImportRequest {
		if (startPage < 1 || maxPages < 1 || maxPages > 20) {
			throw new IllegalArgumentException(
				"Photo award import page range is invalid");
		}
	}
}
