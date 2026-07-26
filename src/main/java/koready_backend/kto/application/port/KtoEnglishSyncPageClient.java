package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoFetchedEnglishSyncPage;

public interface KtoEnglishSyncPageClient {

	KtoFetchedEnglishSyncPage fetchFetchedPage(int pageNumber);
}
