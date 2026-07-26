package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoEnglishStorePageResult;
import koready_backend.kto.application.model.KtoEnglishSyncRequest;
import koready_backend.kto.application.model.KtoFetchedEnglishSyncPage;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoEnglishPageStore;
import koready_backend.kto.application.port.KtoEnglishSyncPageClient;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoEnglishSyncPage;

@ExtendWith(MockitoExtension.class)
class KtoEnglishSyncImportServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-27T03:00:00Z");

	@Mock
	KtoEnglishSyncPageClient client;

	@Mock
	KtoEnglishMatchService matchService;

	@Mock
	KtoRawSnapshotStore snapshotStore;

	@Mock
	KtoEnglishPageStore pageStore;

	@Test
	void stopsAtTheReportedFinalPageAndAggregatesMatchResults() {
		KtoFetchedEnglishSyncPage page126 = page(126, 25_250, 200, "page-126");
		KtoFetchedEnglishSyncPage page127 = page(127, 25_250, 50, "page-127");
		when(client.fetchFetchedPage(126)).thenReturn(page126);
		when(client.fetchFetchedPage(127)).thenReturn(page127);
		when(matchService.match(any(), eq(false))).thenReturn(List.of());
		when(snapshotStore.store(any())).thenReturn(snapshot("126"), snapshot("127"));
		when(pageStore.store(any())).thenReturn(
			new KtoEnglishStorePageResult(1, 1, 200, 160, 10, 30, 158, false),
			new KtoEnglishStorePageResult(2, 2, 50, 40, 2, 8, 39, true));

		var result = service().sync(
			new KtoEnglishSyncRequest(126, 20),
			new KtoBatchExecutionReference(31, 47));

		assertEquals(2, result.processedPages());
		assertEquals(250, result.processedItems());
		assertEquals(200, result.autoMatchedItems());
		assertEquals(12, result.reviewRequiredItems());
		assertEquals(38, result.unmatchedItems());
		assertEquals(197, result.localizedItems());
		assertEquals(1, result.replayedPages());
		assertEquals(127, result.lastProcessedPage());
		assertFalse(result.truncatedByPageLimit());
		verify(client).fetchFetchedPage(126);
		verify(client).fetchFetchedPage(127);
	}

	private KtoEnglishSyncImportService service() {
		return new KtoEnglishSyncImportService(
			client,
			matchService,
			snapshotStore,
			pageStore,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private KtoFetchedEnglishSyncPage page(
		int pageNumber,
		int totalCount,
		int itemCount,
		String payloadText
	) {
		byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
		return new KtoFetchedEnglishSyncPage(
			new KtoEnglishSyncPage(
				pageNumber,
				200,
				totalCount,
				List.of(),
				payload.length,
				sha256(payload)),
			new KtoSuccessfulCallMetadata(NOW.minusSeconds(1), NOW, 1_000, 200),
			payload);
	}

	private KtoStoredSnapshotMetadata snapshot(String suffix) {
		return new KtoStoredSnapshotMetadata(
			"kto/eng/areaBasedSyncList2/20260727/page-" + suffix + ".json.gz",
			"a".repeat(64),
			10,
			NOW);
	}

	private String sha256(byte[] payload) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
