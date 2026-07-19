package koready_backend.location.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.location.kakao")
public record KakaoLocationProperties(
	URI baseUrl,
	String restApiKey,
	int maxResponseBytes,
	Duration connectTimeout,
	Duration readTimeout
) {

	public KakaoLocationProperties {
		Objects.requireNonNull(baseUrl, "Kakao Location base URL is required");
		restApiKey = restApiKey == null ? "" : restApiKey;
		if (!"https".equalsIgnoreCase(baseUrl.getScheme())) {
			throw new IllegalStateException("Kakao Location base URL must use HTTPS");
		}
		if (maxResponseBytes < 1 || maxResponseBytes > 4 * 1024 * 1024) {
			throw new IllegalStateException("Kakao Location response limit is invalid");
		}
		positive(connectTimeout, "connect timeout");
		positive(readTimeout, "read timeout");
	}

	private static void positive(Duration value, String name) {
		Objects.requireNonNull(value, "Kakao Location " + name + " is required");
		if (value.isZero() || value.isNegative()) {
			throw new IllegalStateException("Kakao Location " + name + " must be positive");
		}
	}
}
