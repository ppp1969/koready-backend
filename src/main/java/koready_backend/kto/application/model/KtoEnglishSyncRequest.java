package koready_backend.kto.application.model;

public record KtoEnglishSyncRequest(int startPage, int maxPages) {

	public KtoEnglishSyncRequest {
		if (startPage < 1 || maxPages < 1 || maxPages > 20) {
			throw new IllegalArgumentException("KTO English sync request is invalid");
		}
	}
}
