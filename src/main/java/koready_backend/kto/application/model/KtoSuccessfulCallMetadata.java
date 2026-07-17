package koready_backend.kto.application.model;

import java.time.Instant;
import java.util.Objects;

public record KtoSuccessfulCallMetadata(
	Instant requestedAt,
	Instant responseReceivedAt,
	long durationMs,
	int httpStatus
) {

	public KtoSuccessfulCallMetadata {
		Objects.requireNonNull(requestedAt, "KTO request time is required");
		Objects.requireNonNull(responseReceivedAt, "KTO response time is required");
		if (responseReceivedAt.isBefore(requestedAt)) {
			throw new IllegalArgumentException("KTO response time cannot precede request time");
		}
		if (durationMs < 0) {
			throw new IllegalArgumentException("KTO call duration must not be negative");
		}
		if (httpStatus < 200 || httpStatus >= 300) {
			throw new IllegalArgumentException("KTO successful call must have a 2xx HTTP status");
		}
	}
}
