package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoPhotoGalleryStorePageCommand;
import koready_backend.kto.application.model.KtoPhotoGalleryStorePageResult;

@FunctionalInterface
public interface KtoPhotoGalleryStore {

	KtoPhotoGalleryStorePageResult store(
		KtoPhotoGalleryStorePageCommand command);
}
