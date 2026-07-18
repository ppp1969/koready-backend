package koready_backend.recommendation.application.exception;

public class InvalidDateRangeException extends RuntimeException {

	public InvalidDateRangeException() {
		super("CUSTOM requires both dates, and the start date must not be after the end date.");
	}
}
