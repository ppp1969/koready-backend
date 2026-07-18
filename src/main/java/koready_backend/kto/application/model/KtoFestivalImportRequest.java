package koready_backend.kto.application.model;

import java.time.LocalDate;
import java.util.Objects;

public record KtoFestivalImportRequest(
	LocalDate eventStartDate,
	int startPage,
	int maxPages
) {

	private static final int MAX_PAGES_PER_RUN = 20;

	public KtoFestivalImportRequest {
		Objects.requireNonNull(eventStartDate, "KTO festival import start date is required");
		if (startPage < 1) {
			throw new IllegalArgumentException("KTO festival import start page must be at least 1");
		}
		if (maxPages < 1 || maxPages > MAX_PAGES_PER_RUN) {
			throw new IllegalArgumentException("KTO festival import page limit must be between 1 and 20");
		}
	}
}
