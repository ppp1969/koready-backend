package koready_backend.kto.infrastructure.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.kto.photo-gallery-api")
public record KtoPhotoGalleryApiProperties(
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
	private static final String REQUIRED_PATH_SUFFIX =
		"/B551011/PhotoGalleryService1";

	public KtoPhotoGalleryApiProperties {
		if (baseUrl == null
			|| !"https".equalsIgnoreCase(baseUrl.getScheme())
			|| !REQUIRED_HOST.equalsIgnoreCase(baseUrl.getHost())
			|| baseUrl.getPath() == null
			|| !baseUrl.getPath().endsWith(REQUIRED_PATH_SUFFIX)) {
			throw new IllegalArgumentException(
				"KTO photo gallery API base URL is not approved");
		}
		if (maxResponseBytes < 1
			|| maxResponseBytes > MAX_ALLOWED_RESPONSE_BYTES) {
			throw new IllegalArgumentException(
				"KTO photo gallery response limit is invalid");
		}
		if (connectTimeout == null || connectTimeout.isZero()
			|| connectTimeout.isNegative()
			|| readTimeout == null || readTimeout.isZero()
			|| readTimeout.isNegative()) {
			throw new IllegalArgumentException(
				"KTO photo gallery timeouts must be positive");
		}
		if (mobileOs == null || mobileOs.isBlank()
			|| mobileApp == null || mobileApp.isBlank()) {
			throw new IllegalArgumentException(
				"KTO photo gallery mobile identification is required");
		}
	}
}
