package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoDetailEnrichmentRequest;
import koready_backend.kto.application.model.KtoDetailEnrichmentResult;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoreDetailCommand;
import koready_backend.kto.application.model.KtoStoredDetailOperation;
import koready_backend.kto.application.port.KtoDetailClient;
import koready_backend.kto.application.port.KtoDetailStore;
import koready_backend.kto.application.port.KtoDetailTargetSource;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoDetailOperation;

@Service
public class KtoDetailEnrichmentService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final KtoDetailTargetSource targetSource;
	private final KtoDetailClient client;
	private final KtoRawSnapshotStore snapshotStore;
	private final KtoDetailStore detailStore;
	private final Clock clock;

	@Autowired
	public KtoDetailEnrichmentService(
		KtoDetailTargetSource targetSource,
		KtoDetailClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoDetailStore detailStore
	) {
		this(targetSource, client, snapshotStore, detailStore, Clock.systemUTC());
	}

	KtoDetailEnrichmentService(
		KtoDetailTargetSource targetSource,
		KtoDetailClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoDetailStore detailStore,
		Clock clock
	) {
		this.targetSource = targetSource;
		this.client = client;
		this.snapshotStore = snapshotStore;
		this.detailStore = detailStore;
		this.clock = clock;
	}

	public KtoDetailEnrichmentResult enrich(
		KtoDetailEnrichmentRequest request,
		KtoBatchExecutionReference batchExecution
	) {
		var targets = targetSource.findAfter(
			request.startAfterPlaceId(), request.maxPlaces());
		long lastProcessedPlaceId = request.startAfterPlaceId();
		int successfulOperations = 0;

		for (var target : targets) {
			var storedOperations = new ArrayList<KtoStoredDetailOperation>();
			for (KtoDetailOperation operation : KtoDetailOperation.values()) {
				var fetched = client.fetch(operation, target);
				Instant capturedAt = Instant.now(clock);
				var snapshot = snapshotStore.store(new KtoRawSnapshot(
					"kor",
					operation.apiName(),
					LocalDate.ofInstant(capturedAt, SEOUL),
					Math.toIntExact(target.placeId()),
					fetched.response().responseSha256(),
					fetched.rawPayload(),
					capturedAt));
				storedOperations.add(new KtoStoredDetailOperation(fetched, snapshot));
				successfulOperations++;
			}
			detailStore.store(new KtoStoreDetailCommand(
				target, storedOperations, batchExecution));
			lastProcessedPlaceId = target.placeId();
		}

		boolean hasMore = !targets.isEmpty()
			&& targetSource.existsAfter(lastProcessedPlaceId);
		return new KtoDetailEnrichmentResult(
			targets.size(),
			successfulOperations,
			lastProcessedPlaceId,
			hasMore,
			request.autoContinue());
	}
}
