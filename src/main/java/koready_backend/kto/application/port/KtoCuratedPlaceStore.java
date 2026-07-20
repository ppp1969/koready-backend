package koready_backend.kto.application.port;

import koready_backend.kto.domain.KtoPlaceDetail;
import koready_backend.kto.domain.KtoPlaceImage;
import koready_backend.onboarding.domain.InitialCandidatePlace;

public interface KtoCuratedPlaceStore {

	long upsert(InitialCandidatePlace specification, KtoPlaceDetail detail);

	default long upsert(
		InitialCandidatePlace specification,
		KtoPlaceDetail detail,
		java.util.List<KtoPlaceImage> images
	) {
		return upsert(specification, detail);
	}
}
