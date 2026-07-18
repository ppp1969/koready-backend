package koready_backend.recommendation.domain;

import java.time.LocalDate;

public enum FestivalOccurrenceStatus {
	UPCOMING,
	ONGOING,
	ENDED;

	public static FestivalOccurrenceStatus from(
		LocalDate startDate,
		LocalDate endDate,
		LocalDate today
	) {
		if (today.isBefore(startDate)) {
			return UPCOMING;
		}
		if (today.isAfter(endDate)) {
			return ENDED;
		}
		return ONGOING;
	}
}
