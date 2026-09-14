package koready_backend.editorial.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EditorialSourceComparison {

	private EditorialSourceComparison() {
	}

	public static Result compare(
		EditorialSourceSnapshot before,
		EditorialSourceSnapshot current
	) {
		if (before == null || current == null) {
			return new Result(EditorialSourceChangeType.NONE, List.of());
		}
		List<EditorialSourceChange> changes = new ArrayList<>();
		add(changes, "titleKo", before.titleKo(), current.titleKo());
		add(changes, "titleEn", before.titleEn(), current.titleEn());
		add(changes, "address", before.address(), current.address());
		add(changes, "overviewKo", before.overviewKo(), current.overviewKo());
		add(changes, "facts", before.facts(), current.facts());
		boolean contentChanged = !changes.isEmpty();
		add(changes, "travelStyles", before.travelStyles(), current.travelStyles());
		EditorialSourceChangeType type = contentChanged
			? EditorialSourceChangeType.CONTENT_CHANGED
			: changes.isEmpty()
				? EditorialSourceChangeType.NONE
				: EditorialSourceChangeType.CLASSIFICATION_CHANGED;
		return new Result(type, changes);
	}

	private static void add(
		List<EditorialSourceChange> changes,
		String field,
		String before,
		String after
	) {
		if (!Objects.equals(normalizeNull(before), normalizeNull(after))) {
			changes.add(new EditorialSourceChange(field, before, after));
		}
	}

	private static String normalizeNull(String value) {
		return value == null ? "" : value;
	}

	public record Result(
		EditorialSourceChangeType type,
		List<EditorialSourceChange> changes
	) {
		public Result {
			changes = List.copyOf(changes);
		}
	}
}
