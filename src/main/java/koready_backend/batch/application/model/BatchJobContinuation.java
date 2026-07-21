package koready_backend.batch.application.model;

import java.util.Map;

import koready_backend.batch.domain.BatchJobType;

public record BatchJobContinuation(BatchJobType jobType, Map<String, Object> parameters) {

	public BatchJobContinuation {
		if (jobType == null || parameters == null || parameters.isEmpty()) {
			throw new IllegalArgumentException("Batch continuation is invalid");
		}
		parameters = Map.copyOf(parameters);
	}
}
