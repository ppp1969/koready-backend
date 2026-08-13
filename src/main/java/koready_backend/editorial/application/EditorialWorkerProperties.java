package koready_backend.editorial.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("koready.editorial.worker")
public record EditorialWorkerProperties(
	boolean enabled,
	Duration pollDelay,
	Duration leaseDuration,
	Duration retryDelay,
	int maxAttempts,
	int dailyLimit
) {
	public EditorialWorkerProperties {
		pollDelay = pollDelay == null ? Duration.ofSeconds(30) : pollDelay;
		leaseDuration = leaseDuration == null ? Duration.ofMinutes(5) : leaseDuration;
		retryDelay = retryDelay == null ? Duration.ofMinutes(10) : retryDelay;
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts must be positive");
		}
		if (dailyLimit < 1) {
			throw new IllegalArgumentException("dailyLimit must be positive");
		}
	}
}
