package koready_backend.kto.application.model;

import koready_backend.kto.domain.KtoPhotoGalleryPage;

public record KtoPhotoGalleryStorePageCommand(
	KtoPhotoGalleryPage page,
	KtoSuccessfulCallMetadata call,
	KtoStoredSnapshotMetadata snapshot,
	KtoBatchExecutionReference batchExecution
) {

	public KtoPhotoGalleryStorePageCommand {
		if (page == null || call == null || snapshot == null) {
			throw new IllegalArgumentException(
				"Photo gallery store command is incomplete");
		}
	}
}
