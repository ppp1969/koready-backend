package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoFetchedPhotoGalleryPage;

@FunctionalInterface
public interface KtoPhotoGalleryPageClient {

	KtoFetchedPhotoGalleryPage fetchPage(int pageNumber);
}
