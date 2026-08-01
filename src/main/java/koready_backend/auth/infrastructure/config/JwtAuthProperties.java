package koready_backend.auth.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.security.jwt")
public record JwtAuthProperties(
	String secret,
	String issuer,
	String audience,
	Duration accessTokenTtl,
	Duration refreshTokenTtl
) {

	private static final Duration DEFAULT_ACCESS_TTL = Duration.ofMinutes(15);
	private static final Duration DEFAULT_REFRESH_TTL = Duration.ofDays(30);

	public JwtAuthProperties {
		secret = secret == null ? "" : secret;
		issuer = issuer == null || issuer.isBlank()
			? "https://api.koready.cloud"
			: issuer.strip();
		audience = audience == null || audience.isBlank()
			? "koready-api"
			: audience.strip();
		accessTokenTtl = accessTokenTtl == null
			? DEFAULT_ACCESS_TTL
			: accessTokenTtl;
		refreshTokenTtl = refreshTokenTtl == null
			? DEFAULT_REFRESH_TTL
			: refreshTokenTtl;
		if (accessTokenTtl.isNegative() || accessTokenTtl.isZero()
			|| refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
			throw new IllegalStateException("Authentication token TTL must be positive.");
		}
	}
}
