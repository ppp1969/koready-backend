package koready_backend.kto.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import koready_backend.kto.domain.KtoSyncPage;

class KtoStorePageCommandTest {

	private static final Instant REQUESTED_AT = Instant.parse("2026-07-17T03:00:00Z");
	private static final Instant RECEIVED_AT = Instant.parse("2026-07-17T03:00:01Z");

	@Test
	void acceptsSuccessfulCallAndStoredSnapshotMetadata() {
		KtoSuccessfulCallMetadata call = new KtoSuccessfulCallMetadata(
			REQUESTED_AT,
			RECEIVED_AT,
			771,
			200);
		KtoStoredSnapshotMetadata snapshot = new KtoStoredSnapshotMetadata(
			"kto/kor/areaBasedSyncList2/2026-07-17/page-1.json.gz",
			"b".repeat(64),
			35_000,
			RECEIVED_AT);

		KtoStorePageCommand command = new KtoStorePageCommand(page("a"), call, snapshot);

		assertEquals(1, command.page().pageNumber());
		assertEquals(35_000, command.snapshot().compressedByteSize());
	}

	@Test
	void rejectsInvalidSuccessfulCallMetadata() {
		assertThrows(IllegalArgumentException.class,
			() -> new KtoSuccessfulCallMetadata(RECEIVED_AT, REQUESTED_AT, 771, 200));
		assertThrows(IllegalArgumentException.class,
			() -> new KtoSuccessfulCallMetadata(REQUESTED_AT, RECEIVED_AT, -1, 200));
		assertThrows(IllegalArgumentException.class,
			() -> new KtoSuccessfulCallMetadata(REQUESTED_AT, RECEIVED_AT, 771, 503));
	}

	@Test
	void rejectsUnsafeOrInvalidSnapshotMetadata() {
		assertThrows(IllegalArgumentException.class,
			() -> new KtoStoredSnapshotMetadata("other/page.json.gz", "b".repeat(64), 10, RECEIVED_AT));
		assertThrows(IllegalArgumentException.class,
			() -> new KtoStoredSnapshotMetadata(
				"kto/serviceKey=secret/page.json.gz", "b".repeat(64), 10, RECEIVED_AT));
		assertThrows(IllegalArgumentException.class,
			() -> new KtoStoredSnapshotMetadata(
				"kto/kor/page.json.gz", "not-a-sha", 10, RECEIVED_AT));
		assertThrows(IllegalArgumentException.class,
			() -> new KtoStoredSnapshotMetadata(
				"kto/kor/page.json.gz", "b".repeat(64), -1, RECEIVED_AT));
	}

	private KtoSyncPage page(String hashCharacter) {
		return new KtoSyncPage(1, 200, 0, List.of(), 100, hashCharacter.repeat(64));
	}
}
