package koready_backend.kto.application.model;

import java.time.LocalDate;
import java.util.Objects;

import koready_backend.kto.domain.KtoFestivalPage;

public record KtoStoreFestivalPageCommand(
	LocalDate eventStartDate,
	KtoFestivalPage page,
	KtoSuccessfulCallMetadata call,
	KtoStoredSnapshotMetadata snapshot
) {

	public KtoStoreFestivalPageCommand {
		Objects.requireNonNull(eventStartDate, "KTO festival query start date is required");
		Objects.requireNonNull(page, "KTO festival page is required");
		Objects.requireNonNull(call, "KTO festival call metadata is required");
		Objects.requireNonNull(snapshot, "KTO festival snapshot metadata is required");
		if (snapshot.capturedAt().isBefore(call.responseReceivedAt())) {
			throw new IllegalArgumentException("KTO festival snapshot cannot precede the response");
		}
	}
}
