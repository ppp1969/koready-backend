package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.model.KtoClassificationApplyResult;
import koready_backend.kto.application.model.KtoClassificationCandidate;
import koready_backend.kto.application.model.KtoClassificationDecision;
import koready_backend.kto.application.port.KtoClassificationBackfillStore;
import koready_backend.kto.application.port.KtoClassificationCandidateSource;
import koready_backend.place.domain.TravelStyle;

class KtoClassificationBackfillServiceTest {

	@Test
	void resumesAfterTheStoredCursorAndPersistsOneBoundedPageAtATime() {
		FakeStore store = new FakeStore(10L);
		KtoClassificationCandidateSource source = (after, limit) -> {
			assertEquals(2, limit);
			if (after == 10L) {
				return List.of(
					candidate(11L, "FD", "FD01", "FD010100"),
					candidate(12L, "XX", "XX01", "XX010100"));
			}
			return List.of();
		};
		KtoClassificationBackfillService service =
			new KtoClassificationBackfillService(source, store);

		KtoClassificationApplyResult result = service.run(2, false);

		assertEquals(2, result.processedPlaces());
		assertEquals(1, result.classifiedPlaces());
		assertEquals(1, result.unclassifiedPlaces());
		assertEquals(1, result.automaticMappings());
		assertEquals(12L, result.lastPlaceId());
		assertTrue(result.completed());
		assertEquals(List.of(12L), store.savedCursors);
		assertEquals(Set.of(TravelStyle.LOCAL_FOOD), store.pages.getFirst().getFirst().styles());
	}

	@Test
	void rejectsCandidatesThatDoNotAdvanceTheCursor() {
		FakeStore store = new FakeStore(5L);
		KtoClassificationCandidateSource source =
			(after, limit) -> List.of(candidate(5L, "NA", "NA01", "NA010100"));
		KtoClassificationBackfillService service =
			new KtoClassificationBackfillService(source, store);

		org.junit.jupiter.api.Assertions.assertThrows(
			IllegalStateException.class,
			() -> service.run(100, false));
		assertEquals(1, store.failures);
	}

	private KtoClassificationCandidate candidate(
		long placeId,
		String level1,
		String level2,
		String level3
	) {
		return new KtoClassificationCandidate(
			placeId,
			"kto-" + placeId,
			"place-" + placeId,
			"12",
			level1,
			level2,
			level3,
			true,
			true,
			Set.of());
	}

	private static final class FakeStore implements KtoClassificationBackfillStore {

		private long cursor;
		private final List<List<KtoClassificationDecision>> pages = new ArrayList<>();
		private final List<Long> savedCursors = new ArrayList<>();
		private int failures;

		private FakeStore(long cursor) {
			this.cursor = cursor;
		}

		@Override
		public long loadCheckpoint(String ruleVersion) {
			return cursor;
		}

		@Override
		public void resetCheckpoint(String ruleVersion) {
			cursor = 0L;
		}

		@Override
		public void recordFailure(String ruleVersion) {
			failures++;
		}

		@Override
		public void applyPage(
			String ruleVersion,
			List<KtoClassificationDecision> decisions,
			long lastPlaceId
		) {
			pages.add(List.copyOf(decisions));
			savedCursors.add(lastPlaceId);
			cursor = lastPlaceId;
		}
	}
}
