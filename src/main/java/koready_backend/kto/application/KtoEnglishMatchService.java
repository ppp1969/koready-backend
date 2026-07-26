package koready_backend.kto.application;

import java.util.List;

import org.springframework.stereotype.Service;

import koready_backend.kto.application.port.KtoEnglishMatchCandidateSource;
import koready_backend.kto.domain.KtoEnglishMatchDecision;
import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.domain.KtoEnglishPlaceMatcher;

@Service
public class KtoEnglishMatchService {

	private final KtoEnglishMatchCandidateSource candidateSource;
	private volatile KtoEnglishPlaceMatcher matcher;

	public KtoEnglishMatchService(KtoEnglishMatchCandidateSource candidateSource) {
		this.candidateSource = candidateSource;
	}

	public List<KtoEnglishMatchDecision> match(List<KtoEnglishPlaceItem> items, boolean refresh) {
		KtoEnglishPlaceMatcher current = refresh ? refreshMatcher() : matcher();
		return items.stream().map(current::match).toList();
	}

	private KtoEnglishPlaceMatcher matcher() {
		KtoEnglishPlaceMatcher current = matcher;
		return current == null ? refreshMatcher() : current;
	}

	private synchronized KtoEnglishPlaceMatcher refreshMatcher() {
		matcher = new KtoEnglishPlaceMatcher(candidateSource.loadCandidates());
		return matcher;
	}
}
