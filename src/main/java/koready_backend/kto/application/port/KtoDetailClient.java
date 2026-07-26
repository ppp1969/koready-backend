package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoFetchedDetailOperation;
import koready_backend.kto.domain.KtoDetailOperation;
import koready_backend.kto.domain.KtoDetailTarget;

public interface KtoDetailClient {

	KtoFetchedDetailOperation fetch(KtoDetailOperation operation, KtoDetailTarget target);
}
