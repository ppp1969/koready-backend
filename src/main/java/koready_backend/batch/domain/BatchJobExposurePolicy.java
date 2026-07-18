package koready_backend.batch.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BatchJobExposurePolicy {

	private static final Set<String> LOCATION_KEYS = Set.of(
		"x", "y", "mapx", "mapy", "startx", "starty", "endx", "endy",
		"latitude", "longitude", "lat", "lng", "lon");

	private BatchJobExposurePolicy() {
	}

	public static Map<String, Object> safeParameters(Map<String, Object> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return Map.of();
		}
		return sanitizeMap(parameters);
	}

	private static Map<String, Object> sanitizeMap(Map<?, ?> source) {
		Map<String, Object> safe = new LinkedHashMap<>();
		source.forEach((rawKey, value) -> {
			if (rawKey == null) {
				return;
			}
			String key = String.valueOf(rawKey);
			if (isSensitiveLocationOrSearch(key)) {
				return;
			}
			safe.put(key, isSecret(key) ? "***" : sanitizeValue(value));
		});
		return Collections.unmodifiableMap(safe);
	}

	private static Object sanitizeValue(Object value) {
		if (value instanceof Map<?, ?> map) {
			return sanitizeMap(map);
		}
		if (value instanceof List<?> list) {
			List<Object> safe = new ArrayList<>(list.size());
			list.forEach(item -> safe.add(sanitizeValue(item)));
			return Collections.unmodifiableList(safe);
		}
		if (value == null || value instanceof String || value instanceof Number
			|| value instanceof Boolean) {
			return value;
		}
		return String.valueOf(value);
	}

	private static boolean isSecret(String key) {
		String normalized = normalize(key);
		return normalized.endsWith("key")
			|| normalized.contains("accesskey")
			|| normalized.contains("apikey")
			|| normalized.contains("privatekey")
			|| normalized.contains("authorization")
			|| normalized.contains("password")
			|| normalized.contains("token")
			|| normalized.contains("secret");
	}

	private static boolean isSensitiveLocationOrSearch(String key) {
		String normalized = normalize(key);
		return normalized.contains("address")
			|| normalized.contains("query")
			|| normalized.contains("keyword")
			|| normalized.contains("search")
			|| normalized.contains("coordinate")
			|| normalized.matches("(start|end|origin|destination)(lat|lng|lon|latitude|longitude)")
			|| LOCATION_KEYS.contains(normalized);
	}

	private static String normalize(String key) {
		return key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
	}
}
