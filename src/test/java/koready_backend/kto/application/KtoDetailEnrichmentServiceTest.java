package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoTransportException;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoDetailEnrichmentRequest;
import koready_backend.kto.application.model.KtoFetchedDetailOperation;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoreDetailCommand;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoDetailClient;
import koready_backend.kto.application.port.KtoDetailStore;
import koready_backend.kto.application.port.KtoDetailTargetSource;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoDetailOperation;
import koready_backend.kto.domain.KtoDetailOperationResponse;
import koready_backend.kto.domain.KtoDetailTarget;

@ExtendWith(MockitoExtension.class)
class KtoDetailEnrichmentServiceTest {

	@Mock
	KtoDetailTargetSource targetSource;

	@Mock
	KtoDetailClient client;

	@Mock
	KtoRawSnapshotStore snapshotStore;

	@Mock
	KtoDetailStore detailStore;

	private KtoDetailTarget target;

	@BeforeEach
	void setUp() {
		target = new KtoDetailTarget(41L, "100", "12");
	}

	@Test
	void storesAllFourSnapshotsBeforePersistingOnePlace() throws Exception {
		when(targetSource.findAfter(40L, 10)).thenReturn(List.of(target));
		when(targetSource.existsAfter(41L)).thenReturn(true);
		for (KtoDetailOperation operation : KtoDetailOperation.values()) {
			when(client.fetch(operation, target)).thenReturn(fetched(operation));
		}
		when(snapshotStore.store(any())).thenReturn(new KtoStoredSnapshotMetadata(
			"kto/kor/detail/test.json.gz",
			"a".repeat(64),
			30,
			Instant.parse("2026-07-27T00:00:02Z")));

		var result = service().enrich(
			new KtoDetailEnrichmentRequest(40L, 10, true),
			new KtoBatchExecutionReference(31L, 47L));

		var order = inOrder(client, snapshotStore, detailStore);
		for (KtoDetailOperation operation : KtoDetailOperation.values()) {
			order.verify(client).fetch(operation, target);
			order.verify(snapshotStore).store(any());
		}
		order.verify(detailStore).store(any());
		assertEquals(1, result.processedPlaces());
		assertEquals(1, result.successfulPlaces());
		assertEquals(0, result.failedPlaces());
		assertEquals(4, result.successfulOperations());
		assertEquals(41L, result.lastProcessedPlaceId());
		assertTrue(result.hasMore());
		assertTrue(result.continuationAllowed());
	}

	@Test
	void usesThePlaceIdAsTheStableDetailSnapshotCursor() throws Exception {
		when(targetSource.findAfter(40L, 1)).thenReturn(List.of(target));
		when(targetSource.existsAfter(41L)).thenReturn(false);
		for (KtoDetailOperation operation : KtoDetailOperation.values()) {
			when(client.fetch(operation, target)).thenReturn(fetched(operation));
		}
		when(snapshotStore.store(any())).thenReturn(new KtoStoredSnapshotMetadata(
			"kto/kor/detail/test.json.gz",
			"a".repeat(64),
			30,
			Instant.parse("2026-07-27T00:00:02Z")));

		service().enrich(
			new KtoDetailEnrichmentRequest(40L, 1, false),
			new KtoBatchExecutionReference(31L, 47L));

		ArgumentCaptor<KtoRawSnapshot> snapshots =
			ArgumentCaptor.forClass(KtoRawSnapshot.class);
		verify(snapshotStore, org.mockito.Mockito.times(4)).store(snapshots.capture());
		assertEquals(
			List.of("detailCommon2", "detailIntro2", "detailInfo2", "detailImage2"),
			snapshots.getAllValues().stream().map(KtoRawSnapshot::operation).toList());
		assertTrue(snapshots.getAllValues().stream()
			.allMatch(snapshot -> snapshot.pageNumber() == 41));

		ArgumentCaptor<KtoStoreDetailCommand> command =
			ArgumentCaptor.forClass(KtoStoreDetailCommand.class);
		verify(detailStore).store(command.capture());
		assertEquals(4, command.getValue().operations().size());
	}

	@Test
	void usesTheLatestContentTypeFromCommonForRemainingOperationsAndStorage()
		throws Exception {
		when(targetSource.findAfter(40L, 1)).thenReturn(List.of(target));
		when(targetSource.existsAfter(41L)).thenReturn(false);
		KtoDetailTarget refreshed = new KtoDetailTarget(41L, "100", "14");
		when(client.fetch(KtoDetailOperation.COMMON, target))
			.thenReturn(fetched(KtoDetailOperation.COMMON, Map.of(
				"contentid", "100",
				"contenttypeid", "14")));
		for (KtoDetailOperation operation : List.of(
			KtoDetailOperation.INTRO,
			KtoDetailOperation.INFO,
			KtoDetailOperation.IMAGE)) {
			when(client.fetch(operation, refreshed)).thenReturn(fetched(operation));
		}
		when(snapshotStore.store(any())).thenReturn(new KtoStoredSnapshotMetadata(
			"kto/kor/detail/test.json.gz",
			"a".repeat(64),
			30,
			Instant.parse("2026-07-27T00:00:02Z")));

		service().enrich(
			new KtoDetailEnrichmentRequest(40L, 1, false),
			new KtoBatchExecutionReference(31L, 47L));

		verify(client).fetch(KtoDetailOperation.COMMON, target);
		verify(client).fetch(KtoDetailOperation.INTRO, refreshed);
		verify(client).fetch(KtoDetailOperation.INFO, refreshed);
		verify(client).fetch(KtoDetailOperation.IMAGE, refreshed);
		ArgumentCaptor<KtoStoreDetailCommand> command =
			ArgumentCaptor.forClass(KtoStoreDetailCommand.class);
		verify(detailStore).store(command.capture());
		assertEquals(refreshed, command.getValue().target());
	}

	@Test
	void continuesWithTheNextPlaceAfterOneTransportFailure() throws Exception {
		KtoDetailTarget first = new KtoDetailTarget(41L, "100", "12");
		KtoDetailTarget failed = new KtoDetailTarget(42L, "101", "12");
		KtoDetailTarget third = new KtoDetailTarget(43L, "102", "12");
		when(targetSource.findAfter(40L, 3))
			.thenReturn(List.of(first, failed, third));
		when(targetSource.existsAfter(43L)).thenReturn(true);
		for (KtoDetailOperation operation : KtoDetailOperation.values()) {
			when(client.fetch(operation, first))
				.thenReturn(fetched(operation, Map.of("contentid", "100")));
			when(client.fetch(operation, third))
				.thenReturn(fetched(operation, Map.of("contentid", "102")));
		}
		when(client.fetch(KtoDetailOperation.COMMON, failed))
			.thenThrow(new KtoTransportException());
		when(snapshotStore.store(any())).thenReturn(new KtoStoredSnapshotMetadata(
			"kto/kor/detail/test.json.gz",
			"a".repeat(64),
			30,
			Instant.parse("2026-07-27T00:00:02Z")));

		var result = service().enrich(
			new KtoDetailEnrichmentRequest(40L, 3, true),
			new KtoBatchExecutionReference(31L, 47L));

		assertEquals(3, result.processedPlaces());
		assertEquals(2, result.successfulPlaces());
		assertEquals(1, result.failedPlaces());
		assertEquals(43L, result.lastProcessedPlaceId());
		assertTrue(result.continuationAllowed());
		verify(detailStore, times(2)).store(any());
	}

	@Test
	void stopsAfterThreeConsecutiveRecoverableFailures() {
		KtoDetailTarget first = new KtoDetailTarget(41L, "100", "12");
		KtoDetailTarget second = new KtoDetailTarget(42L, "101", "12");
		KtoDetailTarget third = new KtoDetailTarget(43L, "102", "12");
		KtoDetailTarget fourth = new KtoDetailTarget(44L, "103", "12");
		when(targetSource.findAfter(40L, 4))
			.thenReturn(List.of(first, second, third, fourth));
		when(targetSource.existsAfter(43L)).thenReturn(true);
		for (KtoDetailTarget failed : List.of(first, second, third)) {
			when(client.fetch(KtoDetailOperation.COMMON, failed))
				.thenThrow(new KtoTransportException());
		}

		var result = service().enrich(
			new KtoDetailEnrichmentRequest(40L, 4, true),
			new KtoBatchExecutionReference(31L, 47L));

		assertEquals(3, result.processedPlaces());
		assertEquals(0, result.successfulPlaces());
		assertEquals(3, result.failedPlaces());
		assertFalse(result.continuationAllowed());
		verify(client, never()).fetch(any(), eq(fourth));
	}

	@Test
	void immediatelyPropagatesAQuotaExceededResponse() {
		when(targetSource.findAfter(40L, 1)).thenReturn(List.of(target));
		when(client.fetch(KtoDetailOperation.COMMON, target))
			.thenThrow(new KtoProviderException("22"));

		KtoProviderException exception = assertThrows(
			KtoProviderException.class,
			() -> service().enrich(
				new KtoDetailEnrichmentRequest(40L, 1, true),
				new KtoBatchExecutionReference(31L, 47L)));

		assertEquals("22", exception.providerCode());
		verify(targetSource, never()).existsAfter(any(Long.class));
	}

	private KtoDetailEnrichmentService service() {
		return new KtoDetailEnrichmentService(
			targetSource,
			client,
			snapshotStore,
			detailStore,
			Clock.fixed(Instant.parse("2026-07-27T00:00:02Z"), ZoneOffset.UTC));
	}

	private KtoFetchedDetailOperation fetched(KtoDetailOperation operation)
		throws Exception {
		return fetched(operation, Map.of("contentid", "100"));
	}

	private KtoFetchedDetailOperation fetched(
		KtoDetailOperation operation,
		Map<String, String> item
	) throws Exception {
		byte[] payload = operation.apiName().getBytes(StandardCharsets.UTF_8);
		return new KtoFetchedDetailOperation(
			new KtoDetailOperationResponse(
				operation,
				List.of(item),
				payload.length,
				sha256(payload)),
			new KtoSuccessfulCallMetadata(
				Instant.parse("2026-07-27T00:00:00Z"),
				Instant.parse("2026-07-27T00:00:01Z"),
				1_000,
				200),
			payload);
	}

	private String sha256(byte[] payload) throws Exception {
		return HexFormat.of().formatHex(
			MessageDigest.getInstance("SHA-256").digest(payload));
	}
}
