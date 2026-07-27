package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoFetchedRelatedTourPage;

@FunctionalInterface
public interface KtoRelatedTourPageClient {

	KtoFetchedRelatedTourPage fetchPage(
		String baseYearMonth,
		String areaCode,
		String signguCode,
		int pageNumber);
}
