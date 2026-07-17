package koready_backend.kto.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.kto.batch")
public record KtoBatchProperties(
	int pageSize,
	int flushSize,
	int maxConcurrency
) {

	private static final int MAX_PAGE_SIZE = 500;

	public KtoBatchProperties {
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("KTO batch page size must be between 1 and 500");
		}
		if (flushSize < 1 || flushSize > pageSize) {
			throw new IllegalArgumentException("KTO batch flush size must be between 1 and page size");
		}
		if (maxConcurrency != 1) {
			throw new IllegalArgumentException("KTO batch concurrency must be 1 on the current memory budget");
		}
	}
}
