package koready_backend.kto.application.model;

import java.util.Objects;

import koready_backend.kto.domain.KtoPhotoAwardPage;

public record KtoPhotoAwardStorePageCommand(
	KtoPhotoAwardPage page,
	KtoSuccessfulCallMetadata call,
	KtoStoredSnapshotMetadata snapshot,
	KtoBatchExecutionReference batchExecution
) {

	public KtoPhotoAwardStorePageCommand {
		Objects.requireNonNull(page, "Photo award page is required");
		Objects.requireNonNull(call, "Photo award call metadata is required");
		Objects.requireNonNull(snapshot, "Photo award snapshot is required");
		if (snapshot.capturedAt().isBefore(call.responseReceivedAt())) {
			throw new IllegalArgumentException(
				"Photo award snapshot cannot precede the response");
		}
	}
}
