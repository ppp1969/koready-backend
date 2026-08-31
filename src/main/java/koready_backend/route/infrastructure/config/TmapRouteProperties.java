package koready_backend.route.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.route.tmap")
public record TmapRouteProperties(
	URI baseUrl,
	String appKey,
	int maxResponseBytes,
	Duration connectTimeout,
	Duration readTimeout
) {
	public TmapRouteProperties {
		Objects.requireNonNull(baseUrl, "TMAP base URL is required");
		appKey = appKey == null ? "" : appKey.strip();
		if (!"https".equalsIgnoreCase(baseUrl.getScheme())) {
			throw new IllegalStateException("TMAP base URL must use HTTPS");
		}
		if (maxResponseBytes < 1 || maxResponseBytes > 4 * 1024 * 1024) {
			throw new IllegalStateException("TMAP response limit is invalid");
		}
		if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()
			|| readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
			throw new IllegalStateException("TMAP timeouts must be positive");
		}
	}
}
