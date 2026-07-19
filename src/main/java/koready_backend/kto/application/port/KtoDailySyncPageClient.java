package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoFetchedSyncPage;

public interface KtoDailySyncPageClient {

	KtoFetchedSyncPage fetchFetchedPage(int pageNumber);
}
