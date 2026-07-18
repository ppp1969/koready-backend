package koready_backend.externalapi.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ExternalApiExposurePolicy {

	private static final Set<String> LOCATION_KEYS = Set.of(
		"x", "y", "mapx", "mapy", "startx", "starty", "endx", "endy",
		"latitude", "longitude", "lat", "lng", "lon");

	private ExternalApiExposurePolicy() {
	}

	public static String safeEndpoint(String endpoint) {
		if (endpoint == null || endpoint.isBlank()) {
			return null;
		}
		try {
			URI value = new URI(endpoint);
			if (value.getHost() != null) {
				return new URI(
					value.getScheme(),
					null,
					value.getHost(),
					value.getPort(),
					value.getPath(),
					null,
					null).toString();
			}
			return stripQueryAndFragment(endpoint);
		} catch (URISyntaxException exception) {
			return stripQueryAndFragment(endpoint);
		}
	}

	public static Map<String, String> safeRequestParams(Map<String, Object> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return Map.of();
		}
		Map<String, String> safe = new LinkedHashMap<>();
		parameters.forEach((key, value) -> {
			if (key == null || isSensitiveLocationOrSearch(key)) {
				return;
			}
			safe.put(key, isSecret(key) ? "***" : String.valueOf(value));
		});
		return Map.copyOf(safe);
	}

	private static boolean isSecret(String key) {
		String normalized = normalize(key);
		return normalized.endsWith("key")
			|| normalized.contains("authorization")
			|| normalized.contains("password")
			|| normalized.contains("token")
			|| normalized.contains("secret")
			|| normalized.equals("key");
	}

	private static boolean isSensitiveLocationOrSearch(String key) {
		String normalized = normalize(key);
		return normalized.contains("address")
			|| normalized.contains("query")
			|| normalized.contains("coordinate")
			|| normalized.matches("(start|end|origin|destination)(lat|lng|lon|latitude|longitude)")
			|| LOCATION_KEYS.contains(normalized);
	}

	private static String stripQueryAndFragment(String endpoint) {
		int query = endpoint.indexOf('?');
		int fragment = endpoint.indexOf('#');
		int end = endpoint.length();
		if (query >= 0) {
			end = Math.min(end, query);
		}
		if (fragment >= 0) {
			end = Math.min(end, fragment);
		}
		String withoutQuery = endpoint.substring(0, end);
		int scheme = withoutQuery.indexOf("://");
		int userInfo = withoutQuery.indexOf('@', scheme + 3);
		if (scheme >= 0 && userInfo > scheme) {
			return withoutQuery.substring(0, scheme + 3)
				+ withoutQuery.substring(userInfo + 1);
		}
		return withoutQuery;
	}

	private static String normalize(String key) {
		return key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
	}
}
