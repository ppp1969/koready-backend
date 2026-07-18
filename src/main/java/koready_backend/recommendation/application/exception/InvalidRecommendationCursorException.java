package koready_backend.recommendation.application.exception;

public class InvalidRecommendationCursorException extends RuntimeException {

	public InvalidRecommendationCursorException() {
		super("The cursor is invalid or does not match the current filters.");
	}
}
