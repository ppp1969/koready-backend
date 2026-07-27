package koready_backend.kto.domain;

public record KtoRelatedTourItem(
	String baseYearMonth,
	String areaCode,
	String areaName,
	String signguCode,
	String signguName,
	String sourceTourCode,
	String sourceName,
	String relatedTourCode,
	String relatedName,
	String relatedRegionCode,
	String relatedRegionName,
	String relatedSignguCode,
	String relatedSignguName,
	String categoryLarge,
	String categoryMedium,
	String categorySmall,
	int rank,
	String sourceHash
) {

	public KtoRelatedTourItem {
		baseYearMonth = requiredPattern(
			baseYearMonth, "\\d{6}", "base year month");
		areaCode = required(areaCode, 30, "area code");
		areaName = optional(areaName, 100);
		signguCode = required(signguCode, 30, "signgu code");
		signguName = optional(signguName, 100);
		sourceTourCode = requiredPattern(
			sourceTourCode, "[0-9a-fA-F]{32}", "source tour code");
		sourceName = required(sourceName, 300, "source name");
		relatedTourCode = requiredPattern(
			relatedTourCode, "[0-9a-fA-F]{32}", "related tour code");
		relatedName = required(relatedName, 300, "related name");
		relatedRegionCode = optional(relatedRegionCode, 30);
		relatedRegionName = optional(relatedRegionName, 100);
		relatedSignguCode = optional(relatedSignguCode, 30);
		relatedSignguName = optional(relatedSignguName, 100);
		categoryLarge = optional(categoryLarge, 100);
		categoryMedium = optional(categoryMedium, 100);
		categorySmall = optional(categorySmall, 100);
		if (rank < 1 || rank > 50) {
			throw new IllegalArgumentException(
				"Related tour rank is invalid");
		}
		if (sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException(
				"Related tour source hash is invalid");
		}
	}

	private static String required(
		String value,
		int maxLength,
		String field
	) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
				"Related tour " + field + " is required");
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(
				"Related tour " + field + " is too long");
		}
		return normalized;
	}

	private static String requiredPattern(
		String value,
		String pattern,
		String field
	) {
		String normalized = required(value, 100, field);
		if (!normalized.matches(pattern)) {
			throw new IllegalArgumentException(
				"Related tour " + field + " is invalid");
		}
		return normalized;
	}

	private static String optional(String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(
				"Related tour field is too long");
		}
		return normalized;
	}
}
