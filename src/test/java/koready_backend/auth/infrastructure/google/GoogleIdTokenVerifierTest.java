package koready_backend.auth.infrastructure.google;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import koready_backend.auth.application.exception.InvalidGoogleIdTokenException;
import koready_backend.auth.application.exception.AuthUnavailableException;

class GoogleIdTokenVerifierTest {

	private static final Instant NOW = Instant.parse("2026-08-01T03:00:00Z");
	private static final String ISSUER = "https://accounts.google.com";
	private static final String CLIENT_ID = "koready-web-client.apps.googleusercontent.com";

	@Test
	void acceptsOnlyAValidGoogleIdentityForTheConfiguredAudience() {
		JwtDecoder decoder = token -> token(
			"google-subject-1",
			ISSUER,
			List.of(CLIENT_ID),
			CLIENT_ID,
			true,
			NOW.minusSeconds(60),
			NOW.plusSeconds(300));
		var verifier = verifier(decoder);

		var identity = verifier.verify("signed-google-id-token");

		assertEquals("google-subject-1", identity.providerSubject());
		assertEquals("verified@example.com", identity.email());
	}

	@Test
	void rejectsATokenIssuedForAnotherGoogleClient() {
		JwtDecoder decoder = token -> token(
			"google-subject-1",
			ISSUER,
			List.of("another-client.apps.googleusercontent.com"),
			"another-client.apps.googleusercontent.com",
			true,
			NOW.minusSeconds(60),
			NOW.plusSeconds(300));

		assertThrows(
			InvalidGoogleIdTokenException.class,
			() -> verifier(decoder).verify("wrong-audience-token"));
	}

	@Test
	void rejectsExpiredOrUnverifiedEmailTokens() {
		JwtDecoder expired = token -> token(
			"google-subject-1",
			ISSUER,
			List.of(CLIENT_ID),
			CLIENT_ID,
			true,
			NOW.minusSeconds(600),
			NOW.minusSeconds(1));
		JwtDecoder unverified = token -> token(
			"google-subject-1",
			ISSUER,
			List.of(CLIENT_ID),
			CLIENT_ID,
			false,
			NOW.minusSeconds(60),
			NOW.plusSeconds(300));

		assertThrows(
			InvalidGoogleIdTokenException.class,
			() -> verifier(expired).verify("expired-token"));
		assertThrows(
			InvalidGoogleIdTokenException.class,
			() -> verifier(unverified).verify("unverified-email-token"));
	}

	@Test
	void convertsSignatureFailuresToThePublicAuthenticationError() {
		JwtDecoder decoder = token -> {
			throw new JwtException("signature mismatch");
		};

		assertThrows(
			InvalidGoogleIdTokenException.class,
			() -> verifier(decoder).verify("forged-token"));
	}

	@Test
	void failsAsUnavailableWhenNoGoogleClientIsConfigured() {
		JwtDecoder decoder = token -> {
			throw new AssertionError("Decoder must not be called without a client ID.");
		};
		var verifier = new GoogleIdTokenVerifier(
			decoder,
			Set.of(),
			ISSUER,
			Clock.fixed(NOW, ZoneOffset.UTC));

		assertThrows(
			AuthUnavailableException.class,
			() -> verifier.verify("google-id-token"));
	}

	private static GoogleIdTokenVerifier verifier(JwtDecoder decoder) {
		return new GoogleIdTokenVerifier(
			decoder,
			Set.of(CLIENT_ID),
			ISSUER,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static Jwt token(
		String subject,
		String issuer,
		List<String> audiences,
		String authorizedParty,
		boolean emailVerified,
		Instant issuedAt,
		Instant expiresAt
	) {
		return Jwt.withTokenValue("redacted-test-token")
			.header("alg", "RS256")
			.subject(subject)
			.issuer(issuer)
			.audience(audiences)
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.claim("azp", authorizedParty)
			.claim("email", "verified@example.com")
			.claim("email_verified", emailVerified)
			.build();
	}
}
