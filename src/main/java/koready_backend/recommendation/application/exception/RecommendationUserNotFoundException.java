package koready_backend.recommendation.application.exception;

public class RecommendationUserNotFoundException extends RuntimeException {

	public RecommendationUserNotFoundException() {
		super("Recommendation user was not found.");
	}
}
