package koready_backend.externalapi.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExternalApiExposurePolicyTest {

	@Test
	void removesTheEntireEndpointQueryAndFragment() {
		assertEquals(
			"https://apis.example.com/v1/search",
			ExternalApiExposurePolicy.safeEndpoint(
				"https://apis.example.com/v1/search?serviceKey=secret&query=school#part"));
	}

	@Test
	void removesEndpointUserInfo() {
		assertEquals(
			"https://apis.example.com/v1/search",
			ExternalApiExposurePolicy.safeEndpoint(
				"https://user:password@apis.example.com/v1/search"));
	}

	@Test
	void masksSecretsAndDropsLocationOrSearchParameters() {
		Map<String, Object> raw = new LinkedHashMap<>();
		raw.put("serviceKey", "live-secret");
		raw.put("Authorization", "Bearer token");
		raw.put("appKey", "tmap-secret");
		raw.put("query", "student dormitory");
		raw.put("roadAddress", "private address");
		raw.put("startX", "126.1");
		raw.put("destinationLongitude", "127.1");
		raw.put("pageNo", 3);

		Map<String, String> safe = ExternalApiExposurePolicy.safeRequestParams(raw);

		assertEquals("***", safe.get("serviceKey"));
		assertEquals("***", safe.get("Authorization"));
		assertEquals("***", safe.get("appKey"));
		assertEquals("3", safe.get("pageNo"));
		assertFalse(safe.containsKey("query"));
		assertFalse(safe.containsKey("roadAddress"));
		assertFalse(safe.containsKey("startX"));
		assertFalse(safe.containsKey("destinationLongitude"));
	}
}
