package koready_backend.auth.infrastructure.google;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import koready_backend.auth.application.exception.AuthUnavailableException;
import koready_backend.auth.application.exception.InvalidGoogleIdTokenException;
import koready_backend.auth.application.port.GoogleIdentityVerifier;
import koready_backend.auth.domain.GoogleIdentity;

public final class GoogleIdTokenVerifier implements GoogleIdentityVerifier {

	private final JwtDecoder decoder;
	private final Set<String> allowedClientIds;
	private final String issuer;
	private final Clock clock;

	public GoogleIdTokenVerifier(
		JwtDecoder decoder,
		Set<String> allowedClientIds,
		String issuer
	) {
		this(decoder, allowedClientIds, issuer, Clock.systemUTC());
	}

	GoogleIdTokenVerifier(
		JwtDecoder decoder,
		Set<String> allowedClientIds,
		String issuer,
		Clock clock
	) {
		this.decoder = decoder;
		this.allowedClientIds = Set.copyOf(allowedClientIds);
		this.issuer = issuer;
		this.clock = clock;
	}

	@Override
	public GoogleIdentity verify(String idToken) {
		if (allowedClientIds.isEmpty()) {
			throw new AuthUnavailableException();
		}
		if (idToken == null || idToken.isBlank()) {
			throw new InvalidGoogleIdTokenException();
		}
		try {
			Jwt jwt = decoder.decode(idToken);
			validate(jwt);
			return new GoogleIdentity(
				jwt.getSubject(),
				jwt.getClaimAsString("email"));
		} catch (JwtException | IllegalArgumentException exception) {
			throw new InvalidGoogleIdTokenException();
		}
	}

	private void validate(Jwt jwt) {
		Instant now = clock.instant();
		String subject = jwt.getSubject();
		String tokenIssuer = jwt.getClaimAsString("iss");
		String authorizedParty = jwt.getClaimAsString("azp");
		String email = jwt.getClaimAsString("email");
		Object emailVerified = jwt.getClaims().get("email_verified");

		boolean validAudience = jwt.getAudience().stream()
			.anyMatch(allowedClientIds::contains);
		boolean validAuthorizedParty = authorizedParty == null
			|| allowedClientIds.contains(authorizedParty);
		boolean validTimes = jwt.getExpiresAt() != null
			&& jwt.getExpiresAt().isAfter(now)
			&& (jwt.getIssuedAt() == null || !jwt.getIssuedAt().isAfter(now.plusSeconds(60)));

		if (subject == null || subject.isBlank()
			|| !issuer.equals(tokenIssuer)
			|| !validAudience
			|| !validAuthorizedParty
			|| !validTimes
			|| !Boolean.TRUE.equals(emailVerified)
			|| email == null
			|| email.isBlank()) {
			throw new InvalidGoogleIdTokenException();
		}
	}
}
