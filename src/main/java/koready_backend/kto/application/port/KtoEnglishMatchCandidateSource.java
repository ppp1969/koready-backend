package koready_backend.kto.application.port;

import java.util.List;

import koready_backend.kto.domain.KtoEnglishPlaceCandidate;

public interface KtoEnglishMatchCandidateSource {

	List<KtoEnglishPlaceCandidate> loadCandidates();
}
