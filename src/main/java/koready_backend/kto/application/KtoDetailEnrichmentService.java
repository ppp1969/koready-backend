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
import koready_backend.kto.application.exception.KtoDataConsistencyException;
import koready_backend.kto.application.exception.KtoTransportException;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoDetailEnrichmentRequest;
import koready_backend.kto.application.model.KtoDetailEnrichmentResult;
import koready_backend.kto.application.model.KtoFetchedDetailOperation;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoreDetailCommand;
import koready_backend.kto.application.model.KtoStoredDetailOperation;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.port.KtoDetailClient;
import koready_backend.kto.application.port.KtoDetailStore;
import koready_backend.kto.application.port.KtoDetailTargetSource;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoDetailOperation;
import koready_backend.kto.domain.KtoDetailTarget;

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
				attemptedOperation = KtoDetailOperation.COMMON;
				var common = client.fetch(KtoDetailOperation.COMMON, target);
				Instant commonCapturedAt = Instant.now(clock);
				storedOperations.add(new KtoStoredDetailOperation(
					common,
					storeSnapshot(target, common, commonCapturedAt)));
				successfulOperations++;
				KtoDetailTarget effectiveTarget = effectiveTarget(target, common);
				for (KtoDetailOperation operation : KtoDetailOperation.values()) {
					if (operation == KtoDetailOperation.COMMON) {
						continue;
					}
					attemptedOperation = operation;
					var fetched = client.fetch(operation, effectiveTarget);
					Instant capturedAt = Instant.now(clock);
					var snapshot = storeSnapshot(effectiveTarget, fetched, capturedAt);
					storedOperations.add(new KtoStoredDetailOperation(fetched, snapshot));
					successfulOperations++;
				}
				detailStore.store(new KtoStoreDetailCommand(
					effectiveTarget, storedOperations, batchExecution));
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

	private KtoStoredSnapshotMetadata storeSnapshot(
		KtoDetailTarget target,
		KtoFetchedDetailOperation fetched,
		Instant capturedAt
	) {
		return snapshotStore.store(new KtoRawSnapshot(
			"kor",
			fetched.response().operation().apiName(),
			LocalDate.ofInstant(capturedAt, SEOUL),
			Math.toIntExact(target.placeId()),
			fetched.response().responseSha256(),
			fetched.rawPayload(),
			capturedAt));
	}

	private KtoDetailTarget effectiveTarget(
		KtoDetailTarget storedTarget,
		KtoFetchedDetailOperation common
	) {
		String latestContentTypeId = null;
		for (var item : common.response().items()) {
			String contentId = item.get("contentid");
			if (contentId != null && !storedTarget.contentId().equals(contentId)) {
				throw new KtoDataConsistencyException(
					"KTO common content ID did not match its target");
			}
			String contentTypeId = item.get("contenttypeid");
			if (contentTypeId == null || contentTypeId.isBlank()) {
				continue;
			}
			if (latestContentTypeId != null
				&& !latestContentTypeId.equals(contentTypeId)) {
				throw new KtoDataConsistencyException(
					"KTO common response contained multiple content types");
			}
			latestContentTypeId = contentTypeId;
		}
		if (latestContentTypeId == null
			|| storedTarget.contentTypeId().equals(latestContentTypeId)) {
			return storedTarget;
		}
		return new KtoDetailTarget(
			storedTarget.placeId(), storedTarget.contentId(), latestContentTypeId);
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
