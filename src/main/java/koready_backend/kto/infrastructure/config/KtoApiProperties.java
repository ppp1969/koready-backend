package koready_backend.kto.infrastructure.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.kto.api")
public record KtoApiProperties(
	URI baseUrl,
	String serviceKey,
	int maxResponseBytes,
	Duration connectTimeout,
	Duration readTimeout,
	String mobileOs,
	String mobileApp
) {

	private static final int MAX_ALLOWED_RESPONSE_BYTES = 16 * 1024 * 1024;
	private static final String REQUIRED_HOST = "apis.data.go.kr";
	private static final String REQUIRED_PATH_SUFFIX = "/B551011/KorService2";

	public KtoApiProperties {
		if (baseUrl == null || !"https".equalsIgnoreCase(baseUrl.getScheme())) {
			throw new IllegalArgumentException("KTO API base URL must use HTTPS");
		}
		if (!REQUIRED_HOST.equalsIgnoreCase(baseUrl.getHost())) {
			throw new IllegalArgumentException("KTO API base URL must use the approved host");
		}
		if (baseUrl.getPath() == null || !baseUrl.getPath().endsWith(REQUIRED_PATH_SUFFIX)) {
			throw new IllegalArgumentException("KTO API base URL must point to KorService2");
		}
		if (maxResponseBytes < 1 || maxResponseBytes > MAX_ALLOWED_RESPONSE_BYTES) {
			throw new IllegalArgumentException("KTO response limit must be between 1 byte and 16 MiB");
		}
		if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
			throw new IllegalArgumentException("KTO connect timeout must be positive");
		}
		if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
			throw new IllegalArgumentException("KTO read timeout must be positive");
		}
		if (mobileOs == null || mobileOs.isBlank() || mobileApp == null || mobileApp.isBlank()) {
			throw new IllegalArgumentException("KTO mobile client identification is required");
		}
	}
}
