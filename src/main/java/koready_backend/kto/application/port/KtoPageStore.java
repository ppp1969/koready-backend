package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoStorePageCommand;
import koready_backend.kto.application.model.KtoStorePageResult;

public interface KtoPageStore {

	KtoStorePageResult store(KtoStorePageCommand command);
}
