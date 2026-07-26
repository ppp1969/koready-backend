package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoStoreDetailCommand;

public interface KtoDetailStore {

	void store(KtoStoreDetailCommand command);
}
