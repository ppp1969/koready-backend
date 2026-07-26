package koready_backend.kto.domain;

import java.util.List;

public record KtoEnglishMatchDecision(
	KtoEnglishPlaceItem source,
	KtoEnglishMatchStatus status,
	KtoEnglishMatchMethod method,
	List<KtoEnglishPlaceCandidate> candidates,
	int imageCandidateCount,
	int coordinateCandidateCount
) {

	public KtoEnglishMatchDecision {
		if (source == null || status == null || method == null) {
			throw new IllegalArgumentException("KTO English match decision is incomplete");
		}
		candidates = List.copyOf(candidates);
		if (imageCandidateCount < 0 || coordinateCandidateCount < 0) {
			throw new IllegalArgumentException("KTO English match candidate count is invalid");
		}
		if (status == KtoEnglishMatchStatus.AUTO_CONFIRMED && candidates.size() != 1) {
			throw new IllegalArgumentException("An automatic KTO English match requires one candidate");
		}
		if (status == KtoEnglishMatchStatus.UNMATCHED && !candidates.isEmpty()) {
			throw new IllegalArgumentException("An unmatched KTO English item cannot have candidates");
		}
	}

	public Long confirmedPlaceId() {
		return status == KtoEnglishMatchStatus.AUTO_CONFIRMED
			? candidates.getFirst().placeId()
			: null;
	}
}
