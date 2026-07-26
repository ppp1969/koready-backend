package koready_backend.kto.application.port;

import java.util.List;

import koready_backend.kto.domain.KtoDetailTarget;

public interface KtoDetailTargetSource {

	List<KtoDetailTarget> findAfter(long placeId, int limit);

	boolean existsAfter(long placeId);
}
