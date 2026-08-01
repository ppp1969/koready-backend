package koready_backend.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.auth.application.exception.InvalidRefreshTokenException;
import koready_backend.auth.application.port.AccessTokenPort;
import koready_backend.auth.application.port.AuthRepository;
import koready_backend.auth.application.port.GoogleIdentityVerifier;
import koready_backend.auth.application.port.RefreshTokenCodec;
import koready_backend.auth.application.port.UserPublicIdGenerator;
import koready_backend.auth.domain.AuthUser;
import koready_backend.auth.domain.GoogleIdentity;
import koready_backend.auth.domain.RefreshSession;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.domain.NextStep;

@Service
public class GoogleAuthService {

	private final GoogleIdentityVerifier googleIdentityVerifier;
	private final AuthRepository repository;
	private final AccessTokenPort accessTokens;
	private final RefreshTokenCodec refreshTokens;
	private final UserPublicIdGenerator publicIds;
	private final Clock clock;
	private final Duration refreshTokenTtl;

	@Autowired
	public GoogleAuthService(
		GoogleIdentityVerifier googleIdentityVerifier,
		AuthRepository repository,
		AccessTokenPort accessTokens,
		RefreshTokenCodec refreshTokens,
		UserPublicIdGenerator publicIds,
		AuthPolicy policy
	) {
		this(
			googleIdentityVerifier,
			repository,
			accessTokens,
			refreshTokens,
			publicIds,
			Clock.systemUTC(),
			policy.refreshTokenTtl());
	}

	GoogleAuthService(
		GoogleIdentityVerifier googleIdentityVerifier,
		AuthRepository repository,
		AccessTokenPort accessTokens,
		RefreshTokenCodec refreshTokens,
		UserPublicIdGenerator publicIds,
		Clock clock,
		Duration refreshTokenTtl
	) {
		this.googleIdentityVerifier = googleIdentityVerifier;
		this.repository = repository;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
		this.publicIds = publicIds;
		this.clock = clock;
		this.refreshTokenTtl = refreshTokenTtl;
	}

	@Transactional
	public AuthResult login(String idToken, String deviceId) {
		GoogleIdentity identity = googleIdentityVerifier.verify(idToken);
		Instant now = clock.instant();
		AuthUser user = repository.findByGoogleSubject(identity.providerSubject())
			.map(existing -> repository.updateGoogleIdentity(
				existing.id(), identity, now))
			.orElseGet(() -> repository.createGoogleUser(
				identity, publicIds.newUserPublicId(), now));
		return issue(user, deviceId, now);
	}

	@Transactional
	public AuthResult refresh(String refreshToken, String deviceId) {
		Instant now = clock.instant();
		String tokenHash = refreshTokens.hash(refreshToken);
		String deviceHash = refreshTokens.hash(deviceId);
		RefreshSession session = repository.findRefreshSessionForUpdate(tokenHash)
			.filter(candidate -> candidate.isActiveAt(now))
			.filter(candidate -> Objects.equals(candidate.deviceIdHash(), deviceHash))
			.orElseThrow(InvalidRefreshTokenException::new);
		AuthUser user = repository.findActiveUser(session.userId())
			.orElseThrow(InvalidRefreshTokenException::new);

		repository.revokeRefreshSession(session.id(), now);
		return issue(user, deviceId, now);
	}

	@Transactional
	public void logout(
		String userPublicId,
		String refreshToken,
		String deviceId
	) {
		Instant now = clock.instant();
		String tokenHash = refreshTokens.hash(refreshToken);
		String deviceHash = refreshTokens.hash(deviceId);
		RefreshSession session = repository.findRefreshSessionForUpdate(tokenHash)
			.filter(candidate -> candidate.isActiveAt(now))
			.filter(candidate -> Objects.equals(candidate.deviceIdHash(), deviceHash))
			.orElseThrow(InvalidRefreshTokenException::new);
		AuthUser user = repository.findActiveUser(session.userId())
			.filter(candidate -> candidate.publicId().equals(userPublicId))
			.orElseThrow(InvalidRefreshTokenException::new);

		repository.revokeRefreshSession(session.id(), now);
	}

	private AuthResult issue(AuthUser user, String deviceId, Instant now) {
		AccessTokenPort.IssuedAccessToken accessToken =
			accessTokens.issue(user.publicId(), now);
		String refreshToken = refreshTokens.generate();
		String refreshTokenHash = refreshTokens.hash(refreshToken);
		String deviceHash = refreshTokens.hash(deviceId);
		Instant refreshExpiresAt = now.plus(refreshTokenTtl);
		repository.revokeActiveRefreshSessions(user.id(), deviceHash, now);
		repository.saveRefreshSession(
			user.id(),
			refreshTokenHash,
			deviceHash,
			now,
			refreshExpiresAt);
		return new AuthResult(
			accessToken.value(),
			refreshToken,
			accessToken.expiresAt(),
			refreshExpiresAt,
			new AuthUserSummary(
				user.id(),
				user.publicId(),
				user.email(),
				user.profileImageUrl(),
				user.preferredLanguage()),
			user.signupStatus().nextStep());
	}

	public record AuthResult(
		String accessToken,
		String refreshToken,
		Instant accessTokenExpiresAt,
		Instant refreshTokenExpiresAt,
		AuthUserSummary user,
		NextStep nextStep
	) {
	}

	public record AuthUserSummary(
		long id,
		String publicId,
		String email,
		String profileImageUrl,
		PlaceLanguage preferredLanguage
	) {
	}
}
