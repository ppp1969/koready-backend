package koready_backend.kto.application.model;

import java.util.List;
import java.util.Objects;

import koready_backend.kto.domain.KtoDetailTarget;

public record KtoStoreDetailCommand(
	KtoDetailTarget target,
	List<KtoStoredDetailOperation> operations,
	KtoBatchExecutionReference batchExecution
) {

	public KtoStoreDetailCommand {
		target = Objects.requireNonNull(target, "KTO detail target is required");
		operations = List.copyOf(Objects.requireNonNull(
			operations, "KTO detail operations are required"));
		if (operations.size() != koready_backend.kto.domain.KtoDetailOperation.values().length) {
			throw new IllegalArgumentException("All KTO detail operations are required");
		}
	}
}
