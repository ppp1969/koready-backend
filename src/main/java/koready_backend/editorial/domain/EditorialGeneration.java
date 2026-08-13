package koready_backend.editorial.domain;

import java.util.List;

public record EditorialGeneration(
	LocalizedContent korean,
	LocalizedContent english,
	List<TourismPurposeTag> tags,
	String provider,
	String model,
	Integer inputTokens,
	Integer outputTokens
) {
	public record LocalizedContent(
		String topic,
		String oneLineDescription,
		String shortIntroduction,
		List<String> enjoyPoints
	) {
	}
}
