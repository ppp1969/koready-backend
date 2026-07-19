package koready_backend.evidence.infrastructure.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.evidence-bundle")
public record EvidenceBundleProperties(Path directory, Duration downloadExpiration) {

	private static final Duration MIN_EXPIRATION = Duration.ofMinutes(1);
	private static final Duration MAX_EXPIRATION = Duration.ofMinutes(15);

	public EvidenceBundleProperties {
		directory = directory == null
			? Path.of(System.getProperty("user.home"), ".koready", "evidence-bundles")
			: directory;
		downloadExpiration = downloadExpiration == null ? Duration.ofMinutes(5) : downloadExpiration;
	}

	public Duration requiredDownloadExpiration() {
		if (downloadExpiration.compareTo(MIN_EXPIRATION) < 0
			|| downloadExpiration.compareTo(MAX_EXPIRATION) > 0) {
			throw new IllegalStateException("Evidence bundle download expiration must be between 1 and 15 minutes");
		}
		return downloadExpiration;
	}
}
