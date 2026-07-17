package koready_backend.kto.application.port;

import koready_backend.kto.domain.KtoSyncPage;

public interface KtoSyncPageClient {

	KtoSyncPage fetchPage(int pageNumber);
}
