package koready_backend.kto.application.model;

import java.util.Objects;
import java.time.Instant;

import koready_backend.kto.domain.KtoSyncPage;

public record KtoStorePageCommand(
	KtoSyncPage page,
	KtoSuccessfulCallMetadata call,
	KtoStoredSnapshotMetadata snapshot,
	KtoBatchExecutionReference batchExecution,
	Instant catalogRunStartedAt
) {
	public KtoStorePageCommand(
		KtoSyncPage page,
		KtoSuccessfulCallMetadata call,
		KtoStoredSnapshotMetadata snapshot
	) {
		this(page, call, snapshot, null, null);
	}

	public KtoStorePageCommand(
		KtoSyncPage page,
		KtoSuccessfulCallMetadata call,
		KtoStoredSnapshotMetadata snapshot,
		KtoBatchExecutionReference batchExecution
	) {
		this(page, call, snapshot, batchExecution, null);
	}

	public KtoStorePageCommand {
		Objects.requireNonNull(page, "KTO page is required");
		Objects.requireNonNull(call, "KTO call metadata is required");
		Objects.requireNonNull(snapshot, "KTO snapshot metadata is required");
		if (snapshot.capturedAt().isBefore(call.responseReceivedAt())) {
			throw new IllegalArgumentException("KTO snapshot cannot be captured before the response");
		}
	}
}
