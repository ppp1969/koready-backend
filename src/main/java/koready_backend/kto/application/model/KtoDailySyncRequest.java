package koready_backend.kto.application.model;

public record KtoDailySyncRequest(int startPage, int maxPages) {

	public KtoDailySyncRequest {
		if (startPage < 1 || maxPages < 1 || maxPages > 20) {
			throw new IllegalArgumentException("KTO daily sync request is invalid");
		}
	}
}
