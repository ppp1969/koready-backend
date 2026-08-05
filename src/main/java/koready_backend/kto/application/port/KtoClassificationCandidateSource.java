package koready_backend.kto.application.port;

import java.util.List;

import koready_backend.kto.application.model.KtoClassificationCandidate;

public interface KtoClassificationCandidateSource {

	List<KtoClassificationCandidate> findAfter(long placeId, int limit);
}
