package koready_backend.location.infrastructure.config;

import java.time.Duration;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.location.search")
public record LocationSearchProperties(
	String provider,
	String tokenSecret,
	Duration tokenTtl
) {

	private static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(10);

	public LocationSearchProperties {
		provider = provider == null || provider.isBlank()
			? "disabled"
			: provider.strip().toLowerCase(Locale.ROOT);
		tokenSecret = tokenSecret == null ? "" : tokenSecret;
		tokenTtl = tokenTtl == null ? DEFAULT_TOKEN_TTL : tokenTtl;
		if (tokenTtl.isZero() || tokenTtl.isNegative()
			|| tokenTtl.compareTo(Duration.ofHours(1)) > 0) {
			throw new IllegalStateException("Location search token TTL is invalid");
		}
	}
}
