package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.kto.application.model.KtoDailySyncRequest;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoFetchedSyncPage;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStorePageResult;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoDailySyncPageClient;
import koready_backend.kto.application.port.KtoPageStore;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoSyncPage;

@ExtendWith(MockitoExtension.class)
class KtoDailySyncImportServiceTest {

	@Mock
	KtoDailySyncPageClient client;

	@Mock
	KtoRawSnapshotStore snapshotStore;

	@Mock
	KtoPageStore pageStore;

	@Test
	void storesTheRawPayloadThenPersistsTheNormalizedSyncPage() throws Exception {
		byte[] raw = "[]".getBytes(StandardCharsets.UTF_8);
		KtoSyncPage page = new KtoSyncPage(1, 100, 0, List.of(), raw.length, sha256(raw));
		when(client.fetchFetchedPage(1)).thenReturn(new KtoFetchedSyncPage(
			page,
			new KtoSuccessfulCallMetadata(
				Instant.parse("2026-07-20T00:00:00Z"), Instant.parse("2026-07-20T00:00:01Z"), 1_000, 200),
			raw));
		when(snapshotStore.store(any())).thenReturn(new KtoStoredSnapshotMetadata(
			"kto/kor/areaBasedSyncList2/test.json.gz", "a".repeat(64), 30, Instant.parse("2026-07-20T00:00:02Z")));
		when(pageStore.store(any())).thenReturn(new KtoStorePageResult(1L, 2L, 0, 0, 0, false));

		var result = service().sync(new KtoDailySyncRequest(1, 1));

		ArgumentCaptor<KtoRawSnapshot> snapshot = ArgumentCaptor.forClass(KtoRawSnapshot.class);
		verify(snapshotStore).store(snapshot.capture());
		verify(pageStore).store(any());
		assertEquals("areaBasedSyncList2", snapshot.getValue().operation());
		assertEquals(1, result.processedPages());
		assertEquals(0, result.processedItems());
		assertEquals(0, result.reportedTotalCount());
	}

	@Test
	void passesTheManualBatchReferenceToThePageStore() throws Exception {
		byte[] raw = "[]".getBytes(StandardCharsets.UTF_8);
		KtoSyncPage page = new KtoSyncPage(1, 100, 0, List.of(), raw.length, sha256(raw));
		when(client.fetchFetchedPage(1)).thenReturn(new KtoFetchedSyncPage(
			page,
			new KtoSuccessfulCallMetadata(
				Instant.parse("2026-07-20T00:00:00Z"), Instant.parse("2026-07-20T00:00:01Z"), 1_000, 200),
			raw));
		when(snapshotStore.store(any())).thenReturn(new KtoStoredSnapshotMetadata(
			"kto/kor/areaBasedSyncList2/test.json.gz", "a".repeat(64), 30, Instant.parse("2026-07-20T00:00:02Z")));
		when(pageStore.store(any())).thenReturn(new KtoStorePageResult(1L, 2L, 0, 0, 0, false));

		service().sync(new KtoDailySyncRequest(1, 1), new KtoBatchExecutionReference(31L, 47L));

		ArgumentCaptor<koready_backend.kto.application.model.KtoStorePageCommand> command =
			ArgumentCaptor.forClass(koready_backend.kto.application.model.KtoStorePageCommand.class);
		verify(pageStore).store(command.capture());
		assertEquals(new KtoBatchExecutionReference(31L, 47L), command.getValue().batchExecution());
	}

	private KtoDailySyncImportService service() {
		return new KtoDailySyncImportService(client, snapshotStore, pageStore, Clock.fixed(
			Instant.parse("2026-07-20T00:00:02Z"), ZoneOffset.UTC));
	}

	private static String sha256(byte[] value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
	}
}
