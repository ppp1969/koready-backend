package koready_backend.kto.domain;

import java.util.HashSet;
import java.util.List;

public record KtoPhotoGalleryPage(
	int pageNumber,
	int pageSize,
	int totalCount,
	List<KtoPhotoGalleryItem> items,
	long responseBytes,
	String responseSha256
) {

	public KtoPhotoGalleryPage {
		items = List.copyOf(items);
		if (pageNumber < 1 || pageSize < 1 || totalCount < 0
			|| responseBytes < 0
			|| responseSha256 == null
			|| !responseSha256.matches("[0-9a-f]{64}")
			|| new HashSet<>(items.stream()
				.map(KtoPhotoGalleryItem::contentId)
				.toList()).size() != items.size()) {
			throw new IllegalArgumentException(
				"Photo gallery page metadata is invalid");
		}
	}
}
