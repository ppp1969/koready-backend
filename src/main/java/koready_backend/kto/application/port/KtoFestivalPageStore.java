package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoFestivalStorePageResult;
import koready_backend.kto.application.model.KtoStoreFestivalPageCommand;

public interface KtoFestivalPageStore {

	KtoFestivalStorePageResult store(KtoStoreFestivalPageCommand command);
}
