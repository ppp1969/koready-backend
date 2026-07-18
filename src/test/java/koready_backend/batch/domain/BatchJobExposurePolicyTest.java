package koready_backend.batch.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BatchJobExposurePolicyTest {

	@Test
	void recursivelyMasksSecretsAndRemovesSearchOrLocationValues() {
		Map<String, Object> raw = new LinkedHashMap<>();
		raw.put("serviceKey", "live-secret");
		raw.put("query", "private search");
		raw.put("keyword", "another private search");
		raw.put("searchTerm", "private search term");
		raw.put("pageSize", 100);
		raw.put("provider", Map.of(
			"Authorization", "Bearer token",
			"accessKeyId", "provider-access-key",
			"roadAddress", "private address",
			"retryLimit", 2));

		Map<String, Object> safe = BatchJobExposurePolicy.safeParameters(raw);
		Map<?, ?> provider = (Map<?, ?>) safe.get("provider");

		assertEquals("***", safe.get("serviceKey"));
		assertEquals(100, safe.get("pageSize"));
		assertFalse(safe.containsKey("query"));
		assertFalse(safe.containsKey("keyword"));
		assertFalse(safe.containsKey("searchTerm"));
		assertEquals("***", provider.get("Authorization"));
		assertEquals("***", provider.get("accessKeyId"));
		assertEquals(2, provider.get("retryLimit"));
		assertFalse(provider.containsKey("roadAddress"));
	}
}
