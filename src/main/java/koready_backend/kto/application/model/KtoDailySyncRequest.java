package koready_backend.kto.application.model;

import java.time.Instant;

public record KtoDailySyncRequest(
	int startPage,
	int maxPages,
	Instant catalogRunStartedAt
) {
	public KtoDailySyncRequest(int startPage, int maxPages) {
		this(startPage, maxPages, null);
	}

	public KtoDailySyncRequest {
		if (startPage < 1 || maxPages < 1 || maxPages > 20) {
			throw new IllegalArgumentException("KTO daily sync request is invalid");
		}
	}
}
