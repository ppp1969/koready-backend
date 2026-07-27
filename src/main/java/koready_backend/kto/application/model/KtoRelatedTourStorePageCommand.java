package koready_backend.kto.application.model;

import koready_backend.kto.domain.KtoRelatedTourPage;

public record KtoRelatedTourStorePageCommand(
	String baseYearMonth,
	KtoRelatedTourRegion region,
	KtoRelatedTourPage page,
	KtoSuccessfulCallMetadata call,
	KtoStoredSnapshotMetadata snapshot,
	KtoBatchExecutionReference batchExecution
) {

	public KtoRelatedTourStorePageCommand {
		if (baseYearMonth == null
			|| !baseYearMonth.matches("\\d{6}")
			|| region == null || page == null || call == null
			|| snapshot == null) {
			throw new IllegalArgumentException(
				"Related tour store command is incomplete");
		}
	}
}
