package koready_backend.horitip.domain;

import java.util.HashSet;
import java.util.List;

public record HoriTipTrigger(
	List<HoriTipRouteMode> segmentModes,
	List<String> routeNameContainsAny,
	List<String> segmentStartNameContainsAny,
	List<String> segmentEndNameContainsAny,
	Integer minProviderTotalTimeSeconds,
	Integer minTransferCount,
	Integer minTotalWalkDistanceMeters
) {

	public HoriTipTrigger {
		segmentModes = enumList(segmentModes, 8, "segment modes");
		routeNameContainsAny = textList(
			routeNameContainsAny, 20, 80, "route name conditions");
		segmentStartNameContainsAny = textList(
			segmentStartNameContainsAny, 20, 100, "segment start conditions");
		segmentEndNameContainsAny = textList(
			segmentEndNameContainsAny, 20, 100, "segment end conditions");
		requireNonNegative(minProviderTotalTimeSeconds, "minimum provider total time");
		requireNonNegative(minTransferCount, "minimum transfer count");
		requireNonNegative(minTotalWalkDistanceMeters, "minimum walking distance");
	}

	public boolean hasSegmentCondition() {
		return !segmentModes.isEmpty()
			|| !routeNameContainsAny.isEmpty()
			|| !segmentStartNameContainsAny.isEmpty()
			|| !segmentEndNameContainsAny.isEmpty();
	}

	public boolean hasAnyCondition() {
		return hasSegmentCondition()
			|| minProviderTotalTimeSeconds != null
			|| minTransferCount != null
			|| minTotalWalkDistanceMeters != null;
	}

	private static List<HoriTipRouteMode> enumList(
		List<HoriTipRouteMode> values,
		int maxSize,
		String name
	) {
		if (values == null) {
			throw invalid("Hori Tip " + name + " are required");
		}
		List<HoriTipRouteMode> copied = List.copyOf(values);
		if (copied.size() > maxSize || new HashSet<>(copied).size() != copied.size()) {
			throw invalid("Hori Tip " + name + " must be unique and limited to " + maxSize);
		}
		return copied;
	}

	private static List<String> textList(
		List<String> values,
		int maxSize,
		int maxLength,
		String name
	) {
		if (values == null) {
			throw invalid("Hori Tip " + name + " are required");
		}
		List<String> normalized = values.stream()
			.map(value -> value == null ? null : value.strip())
			.toList();
		if (normalized.size() > maxSize
			|| normalized.stream().anyMatch(value -> value == null
				|| value.isBlank() || value.length() > maxLength)
			|| new HashSet<>(normalized).size() != normalized.size()) {
			throw invalid("Hori Tip " + name + " contain an invalid or duplicated value");
		}
		return List.copyOf(normalized);
	}

	private static void requireNonNegative(Integer value, String name) {
		if (value != null && value < 0) {
			throw invalid("Hori Tip " + name + " cannot be negative");
		}

	}

	private static HoriTipPolicyException invalid(String message) {
		return new HoriTipPolicyException(HoriTipPolicyException.Reason.RULE_INVALID, message);
	}
}
