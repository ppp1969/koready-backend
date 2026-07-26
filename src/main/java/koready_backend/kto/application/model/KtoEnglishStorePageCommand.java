package koready_backend.kto.application.model;

import java.util.List;
import java.util.Objects;

import koready_backend.kto.domain.KtoEnglishMatchDecision;
import koready_backend.kto.domain.KtoEnglishSyncPage;

public record KtoEnglishStorePageCommand(
	KtoEnglishSyncPage page,
	List<KtoEnglishMatchDecision> matches,
	KtoSuccessfulCallMetadata call,
	KtoStoredSnapshotMetadata snapshot,
	KtoBatchExecutionReference batchExecution
) {

	public KtoEnglishStorePageCommand {
		Objects.requireNonNull(page, "KTO English page is required");
		matches = List.copyOf(matches);
		Objects.requireNonNull(call, "KTO English call metadata is required");
		Objects.requireNonNull(snapshot, "KTO English snapshot metadata is required");
		if (matches.size() != page.items().size()) {
			throw new IllegalArgumentException("Every KTO English item requires a match decision");
		}
		if (snapshot.capturedAt().isBefore(call.responseReceivedAt())) {
			throw new IllegalArgumentException("KTO English snapshot cannot precede the response");
		}
	}
}
