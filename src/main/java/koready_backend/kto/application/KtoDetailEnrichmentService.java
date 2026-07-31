package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoTransportException;
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
	private static final int MAX_CONSECUTIVE_RECOVERABLE_FAILURES = 3;
	private static final Logger log =
		LoggerFactory.getLogger(KtoDetailEnrichmentService.class);

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
		int processedPlaces = 0;
		int successfulPlaces = 0;
		int failedPlaces = 0;
		int successfulOperations = 0;
		int consecutiveFailures = 0;
		boolean continuationAllowed = true;

		for (var target : targets) {
			processedPlaces++;
			lastProcessedPlaceId = target.placeId();
			var storedOperations = new ArrayList<KtoStoredDetailOperation>();
			KtoDetailOperation attemptedOperation = null;
			try {
				for (KtoDetailOperation operation : KtoDetailOperation.values()) {
					attemptedOperation = operation;
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
				successfulPlaces++;
				consecutiveFailures = 0;
			} catch (KtoProviderException exception) {
				if (quotaExceeded(exception)) {
					throw exception;
				}
				failedPlaces++;
				consecutiveFailures++;
				logRecoverableFailure(target.placeId(), attemptedOperation, exception);
			} catch (KtoTransportException exception) {
				failedPlaces++;
				consecutiveFailures++;
				logRecoverableFailure(target.placeId(), attemptedOperation, exception);
			}
			if (consecutiveFailures >= MAX_CONSECUTIVE_RECOVERABLE_FAILURES) {
				continuationAllowed = false;
				break;
			}
		}

		boolean hasMore = !targets.isEmpty()
			&& targetSource.existsAfter(lastProcessedPlaceId);
		return new KtoDetailEnrichmentResult(
			processedPlaces,
			successfulPlaces,
			failedPlaces,
			successfulOperations,
			lastProcessedPlaceId,
			hasMore,
			request.autoContinue(),
			continuationAllowed);
	}

	private static boolean quotaExceeded(KtoProviderException exception) {
		return "22".equals(exception.providerCode())
			|| "HTTP_429".equals(exception.providerCode());
	}

	private static void logRecoverableFailure(
		long placeId,
		KtoDetailOperation operation,
		RuntimeException exception
	) {
		log.warn(
			"KTO detail place failed. placeId={}, operation={}, exceptionType={}",
			placeId,
			operation == null ? "UNKNOWN" : operation.apiName(),
			exception.getClass().getSimpleName());
	}
}
