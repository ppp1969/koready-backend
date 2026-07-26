package koready_backend.kto.application.model;

import java.util.Objects;

public record KtoStoredDetailOperation(
	KtoFetchedDetailOperation fetched,
	KtoStoredSnapshotMetadata snapshot
) {

	public KtoStoredDetailOperation {
		fetched = Objects.requireNonNull(fetched, "KTO fetched detail is required");
		snapshot = Objects.requireNonNull(snapshot, "KTO detail snapshot is required");
	}
}
