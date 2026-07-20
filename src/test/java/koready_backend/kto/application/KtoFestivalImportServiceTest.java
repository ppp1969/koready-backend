package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.model.KtoFetchedFestivalPage;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoFestivalImportRequest;
import koready_backend.kto.application.model.KtoFestivalImportResult;
import koready_backend.kto.application.model.KtoFestivalStorePageResult;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoFestivalPageClient;
import koready_backend.kto.application.port.KtoFestivalPageStore;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoFestivalPage;

class KtoFestivalImportServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-18T03:00:02Z");
	private static final LocalDate EVENT_START_DATE = LocalDate.of(2026, 7, 1);

	@Test
	void importsSequentialPagesUntilTheReportedLastPage() throws Exception {
		List<Integer> fetchedPages = new ArrayList<>();
		List<Integer> storedPages = new ArrayList<>();
		KtoFestivalPageClient client = (date, pageNumber) -> {
			fetchedPages.add(pageNumber);
			return fetched(pageNumber, 250);
		};
		KtoRawSnapshotStore snapshotStore = snapshot -> new KtoStoredSnapshotMetadata(
			"kto/kor/searchFestival2/2026-07-18/page-" + snapshot.pageNumber() + ".json.gz",
			"b".repeat(64),
			100,
			snapshot.capturedAt());
		KtoFestivalPageStore pageStore = command -> {
			storedPages.add(command.page().pageNumber());
			return new KtoFestivalStorePageResult(
				command.page().pageNumber(),
				command.page().pageNumber(),
				command.page().items().size(),
				command.page().items().size(),
				false);
		};
		KtoFestivalImportService service = new KtoFestivalImportService(
			client,
			snapshotStore,
			pageStore,
			Clock.fixed(NOW, ZoneOffset.UTC));

		KtoFestivalImportResult result = service.importFestivals(
			new KtoFestivalImportRequest(EVENT_START_DATE, 1, 5));

		assertEquals(List.of(1, 2), fetchedPages);
		assertEquals(List.of(1, 2), storedPages);
		assertEquals(2, result.processedPages());
		assertEquals(250, result.reportedTotalCount());
		assertEquals(2, result.lastProcessedPage());
		assertFalse(result.truncatedByPageLimit());
	}

	@Test
	void honorsTheExplicitPageLimitAndReportsTruncation() throws Exception {
		KtoFestivalPageClient client = (date, pageNumber) -> fetched(pageNumber, 600);
		KtoRawSnapshotStore snapshotStore = snapshot -> new KtoStoredSnapshotMetadata(
			"kto/kor/searchFestival2/2026-07-18/page-" + snapshot.pageNumber() + ".json.gz",
			"b".repeat(64),
			100,
			snapshot.capturedAt());
		KtoFestivalPageStore pageStore = command -> new KtoFestivalStorePageResult(
			command.page().pageNumber(),
			command.page().pageNumber(),
			command.page().items().size(),
			command.page().items().size(),
			false);
		KtoFestivalImportService service = new KtoFestivalImportService(
			client,
			snapshotStore,
			pageStore,
			Clock.fixed(NOW, ZoneOffset.UTC));

		KtoFestivalImportResult result = service.importFestivals(
			new KtoFestivalImportRequest(EVENT_START_DATE, 2, 1));

		assertEquals(1, result.processedPages());
		assertEquals(2, result.lastProcessedPage());
		assertTrue(result.truncatedByPageLimit());
	}

	@Test
	void passesTheManualBatchReferenceToTheFestivalPageStore() throws Exception {
		KtoFestivalPageClient client = (date, pageNumber) -> fetched(pageNumber, 0);
		KtoRawSnapshotStore snapshotStore = snapshot -> new KtoStoredSnapshotMetadata(
			"kto/kor/searchFestival2/2026-07-18/page-1.json.gz", "b".repeat(64), 100, snapshot.capturedAt());
		List<koready_backend.kto.application.model.KtoStoreFestivalPageCommand> commands = new ArrayList<>();
		KtoFestivalPageStore pageStore = command -> {
			commands.add(command);
			return new KtoFestivalStorePageResult(1L, 2L, 0, 0, false);
		};
		KtoFestivalImportService service = new KtoFestivalImportService(
			client, snapshotStore, pageStore, Clock.fixed(NOW, ZoneOffset.UTC));

		service.importFestivals(
			new KtoFestivalImportRequest(EVENT_START_DATE, 1, 1),
			new KtoBatchExecutionReference(31L, 47L));

		assertEquals(1, commands.size());
		assertEquals(new KtoBatchExecutionReference(31L, 47L), commands.getFirst().batchExecution());
	}

	private KtoFetchedFestivalPage fetched(int pageNumber, int totalCount) {
		byte[] payload = ("{\"page\":" + pageNumber + "}").getBytes(StandardCharsets.UTF_8);
		return new KtoFetchedFestivalPage(
			new KtoFestivalPage(
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
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
