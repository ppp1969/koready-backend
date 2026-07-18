package koready_backend.onboarding.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class CandidateSetDraftTest {

	@Test
	void rejectsDuplicatePlacesAndDisplayOrders() {
		CandidateSetPolicyException duplicatePlace = assertThrows(
			CandidateSetPolicyException.class,
			() -> new CandidateSetDraft("Summer", List.of(item(1, 1), item(1, 2))));
		assertEquals(CandidateSetPolicyException.Reason.ITEM_DUPLICATED,
			duplicatePlace.reason());

		CandidateSetPolicyException duplicateOrder = assertThrows(
			CandidateSetPolicyException.class,
			() -> new CandidateSetDraft("Summer", List.of(item(1, 1), item(2, 1))));
		assertEquals(CandidateSetPolicyException.Reason.ITEM_DUPLICATED,
			duplicateOrder.reason());
	}

	@Test
	void allowsAtMostTenItemsWhileEditingDraft() {
		assertDoesNotThrow(() -> new CandidateSetDraft("Empty", List.of()));

		CandidateSetPolicyException exception = assertThrows(
			CandidateSetPolicyException.class,
			() -> new CandidateSetDraft("Too many", IntStream.rangeClosed(1, 11)
				.mapToObj(index -> item(index, ((index - 1) % 10) + 1))
				.toList()));
		assertEquals(CandidateSetPolicyException.Reason.TOO_MANY_ITEMS, exception.reason());
	}

	@Test
	void publishingRequiresExactlyTenReadyPlaces() {
		CandidateSetDraft nineItems = new CandidateSetDraft(
			"Nine",
			IntStream.rangeClosed(1, 9).mapToObj(index -> item(index, index)).toList());
		CandidateSetPolicyException countException = assertThrows(
			CandidateSetPolicyException.class,
			() -> nineItems.requirePublishable(Map.of()));
		assertEquals(CandidateSetPolicyException.Reason.REQUIRES_TEN_ITEMS,
			countException.reason());

		CandidateSetDraft tenItems = new CandidateSetDraft(
			"Ten",
			IntStream.rangeClosed(1, 10).mapToObj(index -> item(index, index)).toList());
		Map<Long, CandidatePlaceReadiness> oneNotReady = IntStream.rangeClosed(1, 10)
			.boxed()
			.collect(java.util.stream.Collectors.toMap(
				Integer::longValue,
				index -> index == 7
					? CandidatePlaceReadiness.notReady(index.longValue(), List.of("MISSING_IMAGE"))
					: CandidatePlaceReadiness.ready(index.longValue())));

		CandidateSetPolicyException readinessException = assertThrows(
			CandidateSetPolicyException.class,
			() -> tenItems.requirePublishable(oneNotReady));
		assertEquals(CandidateSetPolicyException.Reason.PLACE_NOT_READY,
			readinessException.reason());
		assertEquals(List.of(7L), readinessException.placeIds());

		Map<Long, CandidatePlaceReadiness> allReady = IntStream.rangeClosed(1, 10)
			.boxed()
			.collect(java.util.stream.Collectors.toMap(
				Integer::longValue,
				index -> CandidatePlaceReadiness.ready(index.longValue())));
		assertDoesNotThrow(() -> tenItems.requirePublishable(allReady));
	}

	@Test
	void onlyDraftSetsCanBeEdited() {
		assertDoesNotThrow(() -> CandidateSetStatus.DRAFT.requireEditable());

		CandidateSetPolicyException published = assertThrows(
			CandidateSetPolicyException.class,
			CandidateSetStatus.PUBLISHED::requireEditable);
		assertEquals(CandidateSetPolicyException.Reason.NOT_EDITABLE, published.reason());

		assertThrows(
			CandidateSetPolicyException.class,
			CandidateSetStatus.ARCHIVED::requireEditable);
	}

	private static CandidateSetItemDraft item(long placeId, int displayOrder) {
		return new CandidateSetItemDraft(
			placeId,
			displayOrder,
			null,
			"Korean curator message",
			null,
			List.of("tag" + displayOrder),
			null);
	}
}
