package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoRelatedTourStorePageCommand;
import koready_backend.kto.application.model.KtoRelatedTourStorePageResult;

@FunctionalInterface
public interface KtoRelatedTourStore {

	KtoRelatedTourStorePageResult store(
		KtoRelatedTourStorePageCommand command);
}
