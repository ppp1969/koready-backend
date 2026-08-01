package koready_backend.auth.application.port;

import java.time.Instant;
import java.util.Optional;

import koready_backend.auth.domain.AuthUser;
import koready_backend.auth.domain.GoogleIdentity;
import koready_backend.auth.domain.RefreshSession;

public interface AuthRepository {

	Optional<AuthUser> findByGoogleSubject(String providerSubject);

	AuthUser createGoogleUser(
		GoogleIdentity identity,
		String userPublicId,
		Instant now);

	AuthUser updateGoogleIdentity(
		long userId,
		GoogleIdentity identity,
		Instant now);

	Optional<AuthUser> findActiveUser(long userId);

	void saveRefreshSession(
		long userId,
		String tokenHash,
		String deviceIdHash,
		Instant createdAt,
		Instant expiresAt);

	void revokeActiveRefreshSessions(
		long userId,
		String deviceIdHash,
		Instant revokedAt);

	Optional<RefreshSession> findRefreshSessionForUpdate(String tokenHash);

	void revokeRefreshSession(long sessionId, Instant revokedAt);
}
