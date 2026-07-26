package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
		assertEquals(4, result.successfulOperations());
		assertEquals(41L, result.lastProcessedPlaceId());
		assertTrue(result.hasMore());
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
		byte[] payload = operation.apiName().getBytes(StandardCharsets.UTF_8);
		return new KtoFetchedDetailOperation(
			new KtoDetailOperationResponse(
				operation,
				List.of(Map.of("contentid", "100")),
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
