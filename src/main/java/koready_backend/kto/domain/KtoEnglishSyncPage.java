package koready_backend.kto.domain;

import java.util.List;

public record KtoEnglishSyncPage(
	int pageNumber,
	int pageSize,
	int totalCount,
	List<KtoEnglishPlaceItem> items,
	long responseBytes,
	String responseSha256
) {

	public KtoEnglishSyncPage {
		if (pageNumber < 1 || pageSize < 1 || totalCount < 0 || responseBytes < 0) {
			throw new IllegalArgumentException("KTO English page metadata is invalid");
		}
		items = List.copyOf(items);
		if (responseSha256 == null || !responseSha256.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("KTO English response hash is invalid");
		}
	}
}
