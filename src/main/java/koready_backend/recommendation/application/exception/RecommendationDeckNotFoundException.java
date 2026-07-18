package koready_backend.recommendation.application.exception;

public final class RecommendationDeckNotFoundException extends RuntimeException {

	public RecommendationDeckNotFoundException() {
		super("Recommendation deck page was not found");
	}
}
