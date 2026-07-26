package koready_backend.kto.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record KtoDetailOperationResponse(
	KtoDetailOperation operation,
	List<Map<String, String>> items,
	int responseBytes,
	String responseSha256
) {

	public KtoDetailOperationResponse {
		operation = Objects.requireNonNull(operation, "KTO detail operation is required");
		Objects.requireNonNull(items, "KTO detail items are required");
		items = items.stream()
			.map(item -> Map.copyOf(new LinkedHashMap<>(item)))
			.toList();
		if (responseBytes < 1) {
			throw new IllegalArgumentException("KTO detail response bytes must be positive");
		}
		if (responseSha256 == null || !responseSha256.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("KTO detail response hash is invalid");
		}
	}
}
