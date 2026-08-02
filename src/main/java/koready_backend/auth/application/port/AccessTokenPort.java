package koready_backend.auth.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import koready_backend.auth.domain.UserRole;

public interface AccessTokenPort {

	IssuedAccessToken issue(
		String userPublicId,
		UserRole role,
		Instant issuedAt);

	Optional<AuthenticatedAccessToken> verify(String token);

	record IssuedAccessToken(String value, Instant expiresAt) {
	}

	record AuthenticatedAccessToken(String subject, Set<String> roles) {
	}
}
