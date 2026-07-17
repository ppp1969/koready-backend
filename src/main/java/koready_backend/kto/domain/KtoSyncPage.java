package koready_backend.kto.domain;

import java.util.List;

public record KtoSyncPage(
	int pageNumber,
	int pageSize,
	int totalCount,
	List<KtoPlaceItem> items,
	long responseBytes,
	String responseSha256
) {

	public KtoSyncPage {
		if (pageNumber < 1) {
			throw new IllegalArgumentException("KTO page number must be at least 1");
		}
		if (pageSize < 1 || totalCount < 0 || responseBytes < 0) {
			throw new IllegalArgumentException("KTO page metadata is invalid");
		}
		items = List.copyOf(items);
		if (responseSha256 == null || !responseSha256.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("KTO response hash is invalid");
		}
	}
}
