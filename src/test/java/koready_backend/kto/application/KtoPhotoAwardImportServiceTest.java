package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoFetchedPhotoAwardPage;
import koready_backend.kto.application.model.KtoPhotoAwardImportRequest;
import koready_backend.kto.application.model.KtoPhotoAwardImportResult;
import koready_backend.kto.application.model.KtoPhotoAwardStorePageResult;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoPhotoAwardPageClient;
import koready_backend.kto.application.port.KtoPhotoAwardStore;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoPhotoAwardPage;

class KtoPhotoAwardImportServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-27T03:00:02Z");

	@Test
	void importsAllReportedPagesAndUsesThePhotoAwardSnapshotNamespace() {
		List<Integer> fetchedPages = new ArrayList<>();
		List<String> snapshotServices = new ArrayList<>();
		KtoPhotoAwardPageClient client = pageNumber -> {
			fetchedPages.add(pageNumber);
			return fetched(pageNumber, 250);
		};
		KtoRawSnapshotStore snapshotStore = snapshot -> {
			snapshotServices.add(snapshot.service());
			return new KtoStoredSnapshotMetadata(
				"kto/photo-award/phokoAwrdSyncList/20260727/"
					+ "event-start-20260727-page-" + snapshot.pageNumber()
					+ "-aaaaaaaaaaaaaaaa.json.gz",
				"b".repeat(64),
				100,
				snapshot.capturedAt());
		};
		KtoPhotoAwardStore store = command -> new KtoPhotoAwardStorePageResult(
			command.page().pageNumber(),
			command.page().pageNumber(),
			command.page().items().size(),
			false);
		KtoPhotoAwardImportService service = new KtoPhotoAwardImportService(
			client,
			snapshotStore,
			store,
			Clock.fixed(NOW, ZoneOffset.UTC));

		KtoPhotoAwardImportResult result = service.importAwards(
			new KtoPhotoAwardImportRequest(1, 5),
			new KtoBatchExecutionReference(70L, 71L));

		assertEquals(List.of(1, 2), fetchedPages);
		assertEquals(List.of("photo-award", "photo-award"), snapshotServices);
		assertEquals(2, result.processedPages());
		assertEquals(250, result.reportedTotalCount());
		assertFalse(result.truncatedByPageLimit());
	}

	@Test
	void reportsTruncationWhenThePageLimitStopsTheSync() {
		KtoPhotoAwardPageClient client = pageNumber -> fetched(pageNumber, 600);
		KtoRawSnapshotStore snapshotStore = snapshot -> new KtoStoredSnapshotMetadata(
			"kto/photo-award/phokoAwrdSyncList/20260727/"
				+ "event-start-20260727-page-" + snapshot.pageNumber()
				+ "-aaaaaaaaaaaaaaaa.json.gz",
			"b".repeat(64),
			100,
			snapshot.capturedAt());
		KtoPhotoAwardStore store = command -> new KtoPhotoAwardStorePageResult(
			1L, 2L, 0, false);
		KtoPhotoAwardImportService service = new KtoPhotoAwardImportService(
			client,
			snapshotStore,
			store,
			Clock.fixed(NOW, ZoneOffset.UTC));

		KtoPhotoAwardImportResult result = service.importAwards(
			new KtoPhotoAwardImportRequest(2, 1));

		assertEquals(1, result.processedPages());
		assertEquals(2, result.lastProcessedPage());
		assertTrue(result.truncatedByPageLimit());
	}

	private KtoFetchedPhotoAwardPage fetched(
		int pageNumber,
		int totalCount
	) {
		byte[] payload = ("{\"page\":" + pageNumber + "}")
			.getBytes(StandardCharsets.UTF_8);
		return new KtoFetchedPhotoAwardPage(
			new KtoPhotoAwardPage(
				pageNumber,
				200,
				totalCount,
				List.of(),
				payload.length,
				sha256(payload)),
			new KtoSuccessfulCallMetadata(
				NOW.minusSeconds(2), NOW.minusSeconds(1), 1000, 200),
			payload);
	}

	private String sha256(byte[] payload) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(payload));
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
