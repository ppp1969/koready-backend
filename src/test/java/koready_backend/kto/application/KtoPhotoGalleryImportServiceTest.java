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

import koready_backend.kto.application.model.KtoFetchedPhotoGalleryPage;
import koready_backend.kto.application.model.KtoPhotoGalleryImportRequest;
import koready_backend.kto.application.model.KtoPhotoGalleryStorePageResult;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoPhotoGalleryPageClient;
import koready_backend.kto.application.port.KtoPhotoGalleryStore;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoPhotoGalleryPage;

class KtoPhotoGalleryImportServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-27T05:00:02Z");

	@Test
	void importsAllReportedPagesInThePhotoGallerySnapshotNamespace() {
		List<Integer> fetchedPages = new ArrayList<>();
		List<String> snapshotServices = new ArrayList<>();
		KtoPhotoGalleryPageClient client = pageNumber -> {
			fetchedPages.add(pageNumber);
			return fetched(pageNumber, 250);
		};
		KtoRawSnapshotStore snapshotStore = snapshot -> {
			snapshotServices.add(snapshot.service());
			return new KtoStoredSnapshotMetadata(
				"kto/photo-gallery/galleryList1/20260727/"
					+ "event-start-20260727-page-" + snapshot.pageNumber()
					+ "-aaaaaaaaaaaaaaaa.json.gz",
				"b".repeat(64),
				100,
				snapshot.capturedAt());
		};
		KtoPhotoGalleryStore store = command ->
			new KtoPhotoGalleryStorePageResult(
				command.page().pageNumber(),
				command.page().pageNumber(),
				command.page().items().size(),
				false);
		KtoPhotoGalleryImportService service =
			new KtoPhotoGalleryImportService(
				client,
				snapshotStore,
				store,
				Clock.fixed(NOW, ZoneOffset.UTC));

		var result = service.importGallery(
			new KtoPhotoGalleryImportRequest(1, 5));

		assertEquals(List.of(1, 2), fetchedPages);
		assertEquals(
			List.of("photo-gallery", "photo-gallery"),
			snapshotServices);
		assertEquals(2, result.processedPages());
		assertEquals(250, result.reportedTotalCount());
		assertFalse(result.truncatedByPageLimit());
	}

	@Test
	void reportsTruncationWhenThePageLimitStopsTheSync() {
		KtoPhotoGalleryPageClient client =
			pageNumber -> fetched(pageNumber, 600);
		KtoRawSnapshotStore snapshotStore = snapshot ->
			new KtoStoredSnapshotMetadata(
				"kto/photo-gallery/galleryList1/20260727/"
					+ "event-start-20260727-page-" + snapshot.pageNumber()
					+ "-aaaaaaaaaaaaaaaa.json.gz",
				"b".repeat(64),
				100,
				snapshot.capturedAt());
		KtoPhotoGalleryStore store = command ->
			new KtoPhotoGalleryStorePageResult(1L, 2L, 0, false);
		KtoPhotoGalleryImportService service =
			new KtoPhotoGalleryImportService(
				client,
				snapshotStore,
				store,
				Clock.fixed(NOW, ZoneOffset.UTC));

		var result = service.importGallery(
			new KtoPhotoGalleryImportRequest(2, 1));

		assertEquals(1, result.processedPages());
		assertEquals(2, result.lastProcessedPage());
		assertTrue(result.truncatedByPageLimit());
	}

	private KtoFetchedPhotoGalleryPage fetched(
		int pageNumber,
		int totalCount
	) {
		byte[] payload = ("{\"page\":" + pageNumber + "}")
			.getBytes(StandardCharsets.UTF_8);
		return new KtoFetchedPhotoGalleryPage(
			new KtoPhotoGalleryPage(
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
