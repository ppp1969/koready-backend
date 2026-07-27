package koready_backend.kto.domain;

import java.util.HashSet;
import java.util.List;

public record KtoPhotoAwardPage(
	int pageNumber,
	int pageSize,
	int totalCount,
	List<KtoPhotoAwardItem> items,
	long responseBytes,
	String responseSha256
) {

	public KtoPhotoAwardPage {
		if (pageNumber < 1 || pageSize < 1 || totalCount < 0 || responseBytes < 0) {
			throw new IllegalArgumentException("Photo award page metadata is invalid");
		}
		items = List.copyOf(items);
		if (items.size() > pageSize
			|| new HashSet<>(items.stream().map(KtoPhotoAwardItem::contentId).toList())
				.size() != items.size()) {
			throw new IllegalArgumentException("Photo award page items are invalid");
		}
		if (responseSha256 == null || !responseSha256.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("Photo award response hash is invalid");
		}
	}
}
