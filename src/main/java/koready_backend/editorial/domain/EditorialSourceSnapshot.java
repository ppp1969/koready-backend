package koready_backend.editorial.domain;

public record EditorialSourceSnapshot(
	String titleKo,
	String titleEn,
	String address,
	String overviewKo,
	String travelStyles,
	String facts
) {
}
