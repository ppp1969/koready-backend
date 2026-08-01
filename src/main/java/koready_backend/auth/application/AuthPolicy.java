package koready_backend.auth.application;

import java.time.Duration;

public record AuthPolicy(Duration refreshTokenTtl) {

	public AuthPolicy {
		if (refreshTokenTtl == null || refreshTokenTtl.isNegative()
			|| refreshTokenTtl.isZero()) {
			throw new IllegalArgumentException("Refresh token TTL must be positive.");
		}
	}
}
