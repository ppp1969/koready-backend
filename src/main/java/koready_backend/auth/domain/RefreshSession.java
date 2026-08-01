package koready_backend.auth.domain;

import java.time.Instant;

public record RefreshSession(
	long id,
	long userId,
	String tokenHash,
	String deviceIdHash,
	Instant expiresAt,
	Instant revokedAt
) {

	public boolean isActiveAt(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	public RefreshSession revokedAt(Instant instant) {
		return new RefreshSession(
			id, userId, tokenHash, deviceIdHash, expiresAt, instant);
	}
}
