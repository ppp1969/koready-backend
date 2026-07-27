package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoPhotoAwardStorePageCommand;
import koready_backend.kto.application.model.KtoPhotoAwardStorePageResult;

public interface KtoPhotoAwardStore {

	KtoPhotoAwardStorePageResult store(KtoPhotoAwardStorePageCommand command);
}
