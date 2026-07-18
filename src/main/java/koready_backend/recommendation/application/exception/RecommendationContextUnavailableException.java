package koready_backend.recommendation.application.exception;

public final class RecommendationContextUnavailableException extends RuntimeException {

	public RecommendationContextUnavailableException() {
		super("Recommendation user or origin location is unavailable");
	}
}
