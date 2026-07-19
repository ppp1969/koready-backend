package koready_backend.kto.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.kto.snapshot.download")
public record KtoSnapshotDownloadProperties(Duration expiration) {

	private static final Duration MIN_EXPIRATION = Duration.ofMinutes(1);
	private static final Duration MAX_EXPIRATION = Duration.ofMinutes(15);

	public KtoSnapshotDownloadProperties {
		expiration = expiration == null ? Duration.ofMinutes(5) : expiration;
	}

	public Duration requiredExpiration() {
		if (expiration.compareTo(MIN_EXPIRATION) < 0
			|| expiration.compareTo(MAX_EXPIRATION) > 0) {
			throw new IllegalStateException(
				"KTO snapshot download expiration must be between 1 and 15 minutes");
		}
		return expiration;
	}
}
