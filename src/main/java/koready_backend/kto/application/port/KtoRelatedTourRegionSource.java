package koready_backend.kto.application.port;

import java.util.List;

import koready_backend.kto.application.model.KtoRelatedTourRegion;

@FunctionalInterface
public interface KtoRelatedTourRegionSource {

	List<KtoRelatedTourRegion> findAfter(
		String startAfterRegionKey,
		int limit);
}
