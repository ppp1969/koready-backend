package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import koready_backend.kto.application.model.KtoFetchedRelatedTourPage;
import koready_backend.kto.application.model.KtoRelatedTourImportRequest;
import koready_backend.kto.application.model.KtoRelatedTourRegion;
import koready_backend.kto.application.model.KtoRelatedTourStorePageResult;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.application.port.KtoRelatedTourPageClient;
import koready_backend.kto.application.port.KtoRelatedTourRegionSource;
import koready_backend.kto.application.port.KtoRelatedTourStore;
import koready_backend.kto.domain.KtoRelatedTourPage;
import koready_backend.kto.domain.KtoRelatedTourItem;

class KtoRelatedTourImportServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-27T06:00:02Z");

	@Test
	void importsRegionsSequentiallyAndUsesProviderSnapshotNamespace() {
		List<String> calls = new ArrayList<>();
		List<String> snapshotServices = new ArrayList<>();
		List<String> snapshotOperations = new ArrayList<>();
		KtoRelatedTourRegionSource regions =
			(baseYm, after, limit) -> List.of(
			new KtoRelatedTourRegion("11", "11530"),
			new KtoRelatedTourRegion("26", "26110"),
			new KtoRelatedTourRegion("27", "27110"));
		KtoRelatedTourPageClient client =
			(baseYm, areaCode, signguCode, pageNumber) -> {
				calls.add(areaCode + ":" + signguCode + ":" + pageNumber);
				return fetched(pageNumber, pageNumber == 1 ? 250 : 250);
			};
		KtoRawSnapshotStore snapshots = snapshot -> {
			snapshotServices.add(snapshot.service());
			snapshotOperations.add(snapshot.operation());
			return new KtoStoredSnapshotMetadata(
				"kto/related-tour/areaBasedList1/20260727/"
					+ "page-" + snapshot.pageNumber()
					+ "-aaaaaaaaaaaaaaaa.json.gz",
				"b".repeat(64),
				100,
				snapshot.capturedAt());
		};
		KtoRelatedTourStore store = command ->
			new KtoRelatedTourStorePageResult(
				command.page().items().size(), false);
		KtoRelatedTourImportService service =
			new KtoRelatedTourImportService(
				regions,
				client,
				snapshots,
				store,
				Clock.fixed(NOW, ZoneOffset.UTC));

		var result = service.importRelatedTours(
			new KtoRelatedTourImportRequest(
				"202606", "", 2, 5, true));

		assertEquals(List.of(
			"11:11530:1", "11:11530:2",
			"26:26110:1", "26:26110:2"), calls);
		assertEquals(
			List.of(
				"related-tour", "related-tour",
				"related-tour", "related-tour"),
			snapshotServices);
		assertEquals(List.of(
			"areaBasedList12026061111530",
			"areaBasedList12026061111530",
			"areaBasedList12026062626110",
			"areaBasedList12026062626110"),
			snapshotOperations);
		assertEquals(2, result.processedRegions());
		assertEquals("26:26110", result.lastProcessedRegionKey());
		assertTrue(result.hasMore());
		assertTrue(result.autoContinue());
	}

	@Test
	void completesWhenNoRegionRemains() {
		KtoRelatedTourRegionSource regions =
			(baseYm, after, limit) -> List.of(
			new KtoRelatedTourRegion("11", "11530"));
		KtoRelatedTourPageClient client =
			(baseYm, areaCode, signguCode, pageNumber) ->
				fetched(pageNumber, 0);
		KtoRawSnapshotStore snapshots = snapshot ->
			new KtoStoredSnapshotMetadata(
				"kto/related-tour/areaBasedList1/20260727/"
					+ "page-1-aaaaaaaaaaaaaaaa.json.gz",
				"b".repeat(64),
				100,
				snapshot.capturedAt());
		KtoRelatedTourStore store = command ->
			new KtoRelatedTourStorePageResult(0, false);
		KtoRelatedTourImportService service =
			new KtoRelatedTourImportService(
				regions,
				client,
				snapshots,
				store,
				Clock.fixed(NOW, ZoneOffset.UTC));

		var result = service.importRelatedTours(
			new KtoRelatedTourImportRequest(
				"202606", "", 2, 5, false));

		assertEquals(1, result.processedRegions());
		assertFalse(result.hasMore());
		assertFalse(result.autoContinue());
	}

	@Test
	void requestsHistoricalProviderRegionButKeepsCurrentContinuationKey() {
		List<String> calls = new ArrayList<>();
		KtoRelatedTourRegionSource regions =
			(baseYm, after, limit) -> List.of(
				new KtoRelatedTourRegion(
					"12", "12110", "46", "46110"));
		KtoRelatedTourPageClient client =
			(baseYm, areaCode, signguCode, pageNumber) -> {
				calls.add(areaCode + ":" + signguCode);
				return fetched(pageNumber, 0);
			};
		KtoRawSnapshotStore snapshots = snapshot ->
			new KtoStoredSnapshotMetadata(
				"kto/related-tour/areaBasedList12026064646110/"
					+ "20260727/page-1-aaaaaaaaaaaaaaaa.json.gz",
				"b".repeat(64),
				100,
				snapshot.capturedAt());
		KtoRelatedTourStore store = command ->
			new KtoRelatedTourStorePageResult(0, false);
		KtoRelatedTourImportService service =
			new KtoRelatedTourImportService(
				regions,
				client,
				snapshots,
				store,
				Clock.fixed(NOW, ZoneOffset.UTC));

		var result = service.importRelatedTours(
			new KtoRelatedTourImportRequest(
				"202606", "", 1, 1, false));

		assertEquals(List.of("46:46110"), calls);
		assertEquals("12:12110", result.lastProcessedRegionKey());
	}

	@Test
	void rejectsItemsOutsideTheRequestedMonthAndRegion() {
		KtoRelatedTourRegionSource regions =
			(baseYm, after, limit) -> List.of(
			new KtoRelatedTourRegion("11", "11530"));
		KtoRelatedTourPageClient client =
			(baseYm, areaCode, signguCode, pageNumber) ->
				new KtoFetchedRelatedTourPage(
					new KtoRelatedTourPage(
						1,
						200,
						1,
						List.of(new KtoRelatedTourItem(
							"202605",
							"26",
							"부산광역시",
							"26110",
							"중구",
							"1".repeat(32),
							"원본 장소",
							"2".repeat(32),
							"연관 장소",
							"26",
							"부산광역시",
							"26110",
							"중구",
							null,
							null,
							null,
							1,
							"a".repeat(64))),
						100,
						"b".repeat(64)),
					new KtoSuccessfulCallMetadata(
						NOW.minusSeconds(2),
						NOW.minusSeconds(1),
						1000,
						200),
					"{}".getBytes(StandardCharsets.UTF_8));
		KtoRawSnapshotStore snapshots = snapshot -> {
			throw new AssertionError("Invalid page must not be stored");
		};
		KtoRelatedTourStore store = command -> {
			throw new AssertionError("Invalid page must not be stored");
		};
		KtoRelatedTourImportService service =
			new KtoRelatedTourImportService(
				regions,
				client,
				snapshots,
				store,
				Clock.fixed(NOW, ZoneOffset.UTC));

		assertThrows(
			koready_backend.kto.application.exception.KtoResponseParseException.class,
			() -> service.importRelatedTours(
				new KtoRelatedTourImportRequest(
					"202606", "", 1, 1, false)));
	}

	private KtoFetchedRelatedTourPage fetched(
		int pageNumber,
		int totalCount
	) {
		byte[] payload = ("{\"page\":" + pageNumber + "}")
			.getBytes(StandardCharsets.UTF_8);
		return new KtoFetchedRelatedTourPage(
			new KtoRelatedTourPage(
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
