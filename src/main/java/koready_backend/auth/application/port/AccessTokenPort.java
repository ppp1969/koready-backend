package koready_backend.auth.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface AccessTokenPort {

	IssuedAccessToken issue(String userPublicId, Instant issuedAt);

	Optional<AuthenticatedAccessToken> verify(String token);

	record IssuedAccessToken(String value, Instant expiresAt) {
	}

	record AuthenticatedAccessToken(String subject, Set<String> roles) {
	}
}
