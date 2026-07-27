package koready_backend.kto.domain;

import java.util.List;

public record KtoRelatedTourPage(
	int pageNumber,
	int pageSize,
	int totalCount,
	List<KtoRelatedTourItem> items,
	int responseBytes,
	String responseSha256
) {

	public KtoRelatedTourPage {
		if (pageNumber < 1 || pageSize < 1 || totalCount < 0
			|| responseBytes < 1 || responseSha256 == null
			|| !responseSha256.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException(
				"Related tour page metadata is invalid");
		}
		items = List.copyOf(items);
	}
}
