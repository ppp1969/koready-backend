package koready_backend.kto.domain;

import java.util.List;

public record KtoFestivalPage(
	int pageNumber,
	int pageSize,
	int totalCount,
	List<KtoFestivalItem> items,
	long responseBytes,
	String responseSha256
) {

	public KtoFestivalPage {
		if (pageNumber < 1) {
			throw new IllegalArgumentException("KTO festival page number must be at least 1");
		}
		if (pageSize < 1 || totalCount < 0 || responseBytes < 0) {
			throw new IllegalArgumentException("KTO festival page metadata is invalid");
		}
		items = List.copyOf(items);
		if (responseSha256 == null || !responseSha256.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("KTO festival response hash is invalid");
		}
	}
}
