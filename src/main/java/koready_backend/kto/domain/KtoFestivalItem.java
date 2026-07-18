package koready_backend.kto.domain;

import java.time.LocalDate;
import java.util.Objects;

public record KtoFestivalItem(
	KtoPlaceItem place,
	LocalDate startDate,
	LocalDate endDate,
	String progressType,
	String festivalType
) {

	public KtoFestivalItem {
		Objects.requireNonNull(place, "KTO festival place is required");
		Objects.requireNonNull(startDate, "KTO festival start date is required");
		Objects.requireNonNull(endDate, "KTO festival end date is required");
		if (!"15".equals(place.contentTypeId())) {
			throw new IllegalArgumentException("KTO festival content type must be 15");
		}
		if (place.title() == null || place.title().isBlank()) {
			throw new IllegalArgumentException("KTO festival title is required");
		}
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("KTO festival end date cannot precede its start date");
		}
	}

	public int eventYear() {
		return startDate.getYear();
	}

	public LocalDate visibleFrom() {
		return startDate.minusMonths(6);
	}
}
