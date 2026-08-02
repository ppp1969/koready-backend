package koready_backend.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.auth.application.exception.InvalidRefreshTokenException;
import koready_backend.auth.application.port.AccessTokenPort;
import koready_backend.auth.application.port.AuthRepository;
import koready_backend.auth.application.port.GoogleIdentityVerifier;
import koready_backend.auth.application.port.RefreshTokenCodec;
import koready_backend.auth.application.port.UserPublicIdGenerator;
import koready_backend.auth.domain.AuthUser;
import koready_backend.auth.domain.GoogleIdentity;
import koready_backend.auth.domain.RefreshSession;
import koready_backend.auth.domain.UserRole;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.domain.NextStep;
import koready_backend.user.domain.SignupStatus;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T03:00:00Z");
	private static final Duration REFRESH_TTL = Duration.ofDays(30);

	@Mock
	private GoogleIdentityVerifier googleIdentityVerifier;
	@Mock
	private AuthRepository repository;
	@Mock
	private AccessTokenPort accessTokens;
	@Mock
	private RefreshTokenCodec refreshTokens;
	@Mock
	private UserPublicIdGenerator publicIds;

	private GoogleAuthService service;

	@BeforeEach
	void setUp() {
		service = new GoogleAuthService(
			googleIdentityVerifier,
			repository,
			accessTokens,
			refreshTokens,
			publicIds,
			Clock.fixed(NOW, ZoneOffset.UTC),
			REFRESH_TTL);
	}

	@Test
	void firstGoogleLoginCreatesAUserBySubjectAndIssuesKoReadyTokens() {
		GoogleIdentity identity = new GoogleIdentity(
			"google-subject-1", "verified@example.com");
		AuthUser user = user(41L, "usr_a1", "verified@example.com");
		when(googleIdentityVerifier.verify("google-id-token")).thenReturn(identity);
		when(repository.findByGoogleSubject(identity.providerSubject()))
			.thenReturn(Optional.empty());
		when(publicIds.newUserPublicId()).thenReturn("usr_a1");
		when(repository.createGoogleUser(identity, "usr_a1", NOW)).thenReturn(user);
		stubTokenIssue(user, "access-1", "refresh-1", "refresh-hash-1");

		var result = service.login("google-id-token", "device-a");

		assertEquals("access-1", result.accessToken());
		assertEquals("refresh-1", result.refreshToken());
		assertEquals(NextStep.TERMS, result.nextStep());
		assertEquals("usr_a1", result.user().publicId());
		verify(repository).revokeActiveRefreshSessions(
			41L, "device-hash-a", NOW);
		verify(repository).saveRefreshSession(
			41L,
			"refresh-hash-1",
			"device-hash-a",
			NOW,
			NOW.plus(REFRESH_TTL));
	}

	@Test
	void repeatLoginUsesTheSameUserOnlyWhenGoogleSubjectMatches() {
		GoogleIdentity identity = new GoogleIdentity(
			"google-subject-1", "new-address@example.com");
		AuthUser existing = user(41L, "usr_a1", "old-address@example.com");
		when(googleIdentityVerifier.verify("google-id-token")).thenReturn(identity);
		when(repository.findByGoogleSubject(identity.providerSubject()))
			.thenReturn(Optional.of(existing));
		when(repository.updateGoogleIdentity(existing.id(), identity, NOW))
			.thenReturn(user(41L, "usr_a1", "new-address@example.com"));
		stubTokenIssue(
			user(41L, "usr_a1", "new-address@example.com"),
			"access-2",
			"refresh-2",
			"refresh-hash-2");

		var result = service.login("google-id-token", "device-a");

		assertEquals("usr_a1", result.user().publicId());
		assertEquals("new-address@example.com", result.user().email());
		verify(repository, never()).createGoogleUser(any(), any(), any());
	}

	@Test
	void refreshRotatesTheStoredTokenAndRejectsAReusedSession() {
		AuthUser user = user(
			41L,
			"usr_a1",
			"verified@example.com",
			UserRole.ADMIN);
		RefreshSession session = new RefreshSession(
			7L,
			41L,
			"old-refresh-hash",
			"device-hash-a",
			NOW.plus(Duration.ofDays(5)),
			null);
		when(refreshTokens.hash("old-refresh")).thenReturn("old-refresh-hash");
		when(refreshTokens.hash("device-a")).thenReturn("device-hash-a");
		when(repository.findRefreshSessionForUpdate("old-refresh-hash"))
			.thenReturn(Optional.of(session))
			.thenReturn(Optional.of(session.revokedAt(NOW)));
		when(repository.findActiveUser(41L)).thenReturn(Optional.of(user));
		when(accessTokens.issue(user.publicId(), UserRole.ADMIN, NOW))
			.thenReturn(new AccessTokenPort.IssuedAccessToken(
				"new-access", NOW.plusSeconds(900)));
		when(refreshTokens.generate()).thenReturn("new-refresh");
		when(refreshTokens.hash("new-refresh")).thenReturn("new-refresh-hash");

		var result = service.refresh("old-refresh", "device-a");

		assertEquals("new-access", result.accessToken());
		assertEquals("new-refresh", result.refreshToken());
		assertNotEquals("old-refresh", result.refreshToken());
		verify(repository).revokeRefreshSession(7L, NOW);
		verify(repository).saveRefreshSession(
			41L,
			"new-refresh-hash",
			"device-hash-a",
			NOW,
			NOW.plus(REFRESH_TTL));

		assertThrows(
			InvalidRefreshTokenException.class,
			() -> service.refresh("old-refresh", "device-a"));
	}

	@Test
	void logoutRejectsAnotherUsersSessionAndRevokesTheOwnersSession() {
		AuthUser user = user(41L, "usr_a1", "verified@example.com");
		RefreshSession session = new RefreshSession(
			7L,
			41L,
			"refresh-hash",
			"device-hash-a",
			NOW.plus(Duration.ofDays(5)),
			null);
		when(refreshTokens.hash("refresh-token")).thenReturn("refresh-hash");
		when(refreshTokens.hash("device-a")).thenReturn("device-hash-a");
		when(repository.findRefreshSessionForUpdate("refresh-hash"))
			.thenReturn(Optional.of(session))
			.thenReturn(Optional.of(session));
		when(repository.findActiveUser(41L)).thenReturn(Optional.of(user));

		assertThrows(
			InvalidRefreshTokenException.class,
			() -> service.logout("usr_other", "refresh-token", "device-a"));

		service.logout("usr_a1", "refresh-token", "device-a");
		verify(repository).revokeRefreshSession(7L, NOW);
	}

	private void stubTokenIssue(
		AuthUser user,
		String accessToken,
		String refreshToken,
		String refreshHash
	) {
		when(accessTokens.issue(user.publicId(), user.role(), NOW))
			.thenReturn(new AccessTokenPort.IssuedAccessToken(
				accessToken, NOW.plusSeconds(900)));
		when(refreshTokens.generate()).thenReturn(refreshToken);
		when(refreshTokens.hash(refreshToken)).thenReturn(refreshHash);
		when(refreshTokens.hash("device-a")).thenReturn("device-hash-a");
	}

	private static AuthUser user(long id, String publicId, String email) {
		return user(id, publicId, email, UserRole.USER);
	}

	private static AuthUser user(
		long id,
		String publicId,
		String email,
		UserRole role
	) {
		return new AuthUser(
			id,
			publicId,
			email,
			role,
			PlaceLanguage.KO,
			SignupStatus.NEED_TERMS,
			null);
	}
}
