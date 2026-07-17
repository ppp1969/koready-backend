package koready_backend.kto.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KtoBatchPropertiesTest {

	@Test
	void acceptsMemoryBoundedDefaults() {
		KtoBatchProperties properties = new KtoBatchProperties(200, 50, 1);

		assertEquals(200, properties.pageSize());
		assertEquals(50, properties.flushSize());
		assertEquals(1, properties.maxConcurrency());
	}

	@Test
	void rejectsPageSizeOutsideSupportedRange() {
		assertThrows(IllegalArgumentException.class, () -> new KtoBatchProperties(0, 1, 1));
		assertThrows(IllegalArgumentException.class, () -> new KtoBatchProperties(501, 50, 1));
	}

	@Test
	void rejectsFlushSizeLargerThanPage() {
		assertThrows(IllegalArgumentException.class, () -> new KtoBatchProperties(200, 201, 1));
	}

	@Test
	void permitsOnlyOneConcurrentBatchOnStagingBudget() {
		assertThrows(IllegalArgumentException.class, () -> new KtoBatchProperties(200, 50, 0));
		assertThrows(IllegalArgumentException.class, () -> new KtoBatchProperties(200, 50, 2));
	}
}
