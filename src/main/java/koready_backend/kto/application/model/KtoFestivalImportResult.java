package koready_backend.kto.application.model;

import java.time.LocalDate;
import java.util.Objects;

public record KtoFestivalImportResult(
	LocalDate eventStartDate,
	int startPage,
	int processedPages,
	int processedItems,
	int replayedPages,
	int reportedTotalCount,
	int lastProcessedPage,
	boolean truncatedByPageLimit
) {

	public KtoFestivalImportResult {
		Objects.requireNonNull(eventStartDate, "KTO festival import result date is required");
		if (startPage < 1 || processedPages < 1 || processedItems < 0
			|| replayedPages < 0 || replayedPages > processedPages
			|| reportedTotalCount < 0 || lastProcessedPage < startPage) {
			throw new IllegalArgumentException("KTO festival import result is invalid");
		}
	}
}
