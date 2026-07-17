package koready_backend.kto.application.model;

import java.util.Objects;

import koready_backend.kto.domain.KtoSyncPage;

public record KtoStorePageCommand(
	KtoSyncPage page,
	KtoSuccessfulCallMetadata call,
	KtoStoredSnapshotMetadata snapshot
) {

	public KtoStorePageCommand {
		Objects.requireNonNull(page, "KTO page is required");
		Objects.requireNonNull(call, "KTO call metadata is required");
		Objects.requireNonNull(snapshot, "KTO snapshot metadata is required");
		if (snapshot.capturedAt().isBefore(call.responseReceivedAt())) {
			throw new IllegalArgumentException("KTO snapshot cannot be captured before the response");
		}
	}
}
