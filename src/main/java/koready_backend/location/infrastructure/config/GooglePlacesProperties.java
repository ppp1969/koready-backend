package koready_backend.location.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.location.google-places")
public record GooglePlacesProperties(
	URI baseUrl,
	String apiKey,
	int maxResponseBytes,
	Duration connectTimeout,
	Duration readTimeout
) {

	public GooglePlacesProperties {
		Objects.requireNonNull(baseUrl, "Google Places base URL is required");
		apiKey = apiKey == null ? "" : apiKey.strip();
		if (!"https".equalsIgnoreCase(baseUrl.getScheme())) {
			throw new IllegalStateException("Google Places base URL must use HTTPS");
		}
		if (maxResponseBytes < 1 || maxResponseBytes > 4 * 1024 * 1024) {
			throw new IllegalStateException("Google Places response limit is invalid");
		}
		positive(connectTimeout, "connect timeout");
		positive(readTimeout, "read timeout");
	}

	private static void positive(Duration value, String name) {
		Objects.requireNonNull(value, "Google Places " + name + " is required");
		if (value.isZero() || value.isNegative()) {
			throw new IllegalStateException("Google Places " + name + " must be positive");
		}
	}
}
