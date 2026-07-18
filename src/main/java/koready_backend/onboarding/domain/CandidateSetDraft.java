package koready_backend.onboarding.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public record CandidateSetDraft(String title, List<CandidateSetItemDraft> items) {

	private static final int PUBLISHED_ITEM_COUNT = 10;

	public CandidateSetDraft {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("Candidate set title is required");
		}
		title = title.strip();
		if (title.length() > 100) {
			throw new IllegalArgumentException("Candidate set title is too long");
		}
		items = List.copyOf(items);
		if (items.size() > PUBLISHED_ITEM_COUNT) {
			throw new CandidateSetPolicyException(
				CandidateSetPolicyException.Reason.TOO_MANY_ITEMS,
				"A candidate set can contain at most ten items");
		}
		if (new HashSet<>(items.stream().map(CandidateSetItemDraft::placeId).toList()).size()
			!= items.size()
			|| new HashSet<>(items.stream().map(CandidateSetItemDraft::displayOrder).toList()).size()
			!= items.size()) {
			throw new CandidateSetPolicyException(
				CandidateSetPolicyException.Reason.ITEM_DUPLICATED,
				"Place IDs and display orders must be unique");
		}
	}

	public void requirePublishable(Map<Long, CandidatePlaceReadiness> readinessByPlaceId) {
		if (items.size() != PUBLISHED_ITEM_COUNT) {
			throw new CandidateSetPolicyException(
				CandidateSetPolicyException.Reason.REQUIRES_TEN_ITEMS,
				"A published candidate set requires exactly ten items");
		}
		List<Long> notReady = items.stream()
			.filter(item -> {
				CandidatePlaceReadiness readiness = readinessByPlaceId.get(item.placeId());
				return readiness == null || !readiness.ready();
			})
			.map(CandidateSetItemDraft::placeId)
			.toList();
		if (!notReady.isEmpty()) {
			throw new CandidateSetPolicyException(
				CandidateSetPolicyException.Reason.PLACE_NOT_READY,
				"Every candidate place must be ready for publication",
				notReady);
		}
	}
}
