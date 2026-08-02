package koready_backend.auth.infrastructure.token;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

import koready_backend.auth.application.exception.AuthUnavailableException;
import koready_backend.auth.application.port.AccessTokenPort;
import koready_backend.auth.domain.UserRole;

public final class JwtAccessTokenService implements AccessTokenPort {

	private static final String TOKEN_TYPE = "access";

	private final String issuer;
	private final String audience;
	private final Duration ttl;
	private final Clock clock;
	private final JwtEncoder encoder;
	private final JwtDecoder decoder;

	public JwtAccessTokenService(
		String secret,
		String issuer,
		String audience,
		Duration ttl
	) {
		this(secret, issuer, audience, ttl, Clock.systemUTC());
	}

	JwtAccessTokenService(
		String secret,
		String issuer,
		String audience,
		Duration ttl,
		Clock clock
	) {
		this.issuer = issuer;
		this.audience = audience;
		this.ttl = ttl;
		this.clock = clock;
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			this.encoder = null;
			this.decoder = null;
			return;
		}
		SecretKey key = new SecretKeySpec(
			secret.getBytes(StandardCharsets.UTF_8),
			"HmacSHA256");
		this.encoder = NimbusJwtEncoder.withSecretKey(key).build();
		NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		jwtDecoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
		this.decoder = jwtDecoder;
	}

	@Override
	public IssuedAccessToken issue(
		String userPublicId,
		UserRole role,
		Instant issuedAt
	) {
		if (encoder == null) {
			throw new AuthUnavailableException();
		}
		if (role == null) {
			throw new IllegalArgumentException("User role is required");
		}
		Instant expiresAt = issuedAt.plus(ttl);
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
			.type("JWT")
			.build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(issuer)
			.audience(List.of(audience))
			.subject(userPublicId)
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.id(UUID.randomUUID().toString())
			.claim("typ", TOKEN_TYPE)
			.claim("roles", Set.of(role.name()))
			.build();
		String value = encoder.encode(
			JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedAccessToken(value, expiresAt);
	}

	@Override
	public Optional<AuthenticatedAccessToken> verify(String token) {
		if (decoder == null || token == null || token.isBlank()) {
			return Optional.empty();
		}
		try {
			var jwt = decoder.decode(token);
			Instant now = clock.instant();
			if (!issuer.equals(jwt.getClaimAsString("iss"))
				|| !jwt.getAudience().contains(audience)
				|| !TOKEN_TYPE.equals(jwt.getClaimAsString("typ"))
				|| jwt.getExpiresAt() == null
				|| !jwt.getExpiresAt().isAfter(now)
				|| jwt.getSubject() == null
				|| jwt.getSubject().isBlank()) {
				return Optional.empty();
			}
			List<String> roles = jwt.getClaimAsStringList("roles");
			if (roles == null || roles.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(new AuthenticatedAccessToken(
				jwt.getSubject(), Set.copyOf(roles)));
		} catch (JwtException | IllegalArgumentException exception) {
			return Optional.empty();
		}
	}
}
