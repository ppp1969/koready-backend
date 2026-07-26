package koready_backend.kto.application.port;

import java.util.Collection;
import java.util.Map;

import koready_backend.kto.domain.KtoEnglishPlaceItem;

public interface KtoEnglishReviewSourceReader {

	Map<String, KtoEnglishPlaceItem> findAll(
		String storageKey,
		Collection<String> sourceContentIds
	);
}
