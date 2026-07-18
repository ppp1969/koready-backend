package koready_backend.batch.application.exception;

public final class InvalidBatchJobPeriodException extends RuntimeException {

	public InvalidBatchJobPeriodException() {
		super("The batch job lookup period is invalid.");
	}
}
