package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoEnglishStorePageCommand;
import koready_backend.kto.application.model.KtoEnglishStorePageResult;

public interface KtoEnglishPageStore {

	KtoEnglishStorePageResult store(KtoEnglishStorePageCommand command);
}
