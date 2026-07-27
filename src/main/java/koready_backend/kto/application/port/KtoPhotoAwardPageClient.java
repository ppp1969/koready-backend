package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoFetchedPhotoAwardPage;

public interface KtoPhotoAwardPageClient {

	KtoFetchedPhotoAwardPage fetchPage(int pageNumber);
}
