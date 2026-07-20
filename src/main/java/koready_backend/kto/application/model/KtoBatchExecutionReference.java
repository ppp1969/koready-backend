package koready_backend.kto.application.model;

/**
 * Identifies the manual batch execution that caused a KTO API call.
 */
public record KtoBatchExecutionReference(long jobId, long jobItemId) {

	public KtoBatchExecutionReference {
		if (jobId <= 0 || jobItemId <= 0) {
			throw new IllegalArgumentException("KTO batch execution identifiers must be positive");
		}
	}
}
