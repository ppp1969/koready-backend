package koready_backend.auth.infrastructure.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import koready_backend.auth.application.exception.AuthUnavailableException;

class JwtAccessTokenServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T03:00:00Z");
	private static final String SECRET =
		"test-only-secret-with-at-least-thirty-two-bytes";

	@Test
	void issuesAndVerifiesAnAccessTokenWithThePublicUserId() {
		var service = service(SECRET);

		var issued = service.issue("usr_a1", NOW);
		var authenticated = service.verify(issued.value()).orElseThrow();

		assertEquals(NOW.plus(Duration.ofMinutes(15)), issued.expiresAt());
		assertEquals("usr_a1", authenticated.subject());
		assertEquals(java.util.Set.of("USER"), authenticated.roles());
	}

	@Test
	void aTokenSignedWithAnotherSecretIsRejected() {
		var issuer = service(SECRET);
		var verifier = service(
			"another-test-secret-with-at-least-thirty-two-bytes");

		assertTrue(verifier.verify(issuer.issue("usr_a1", NOW).value()).isEmpty());
	}

	@Test
	void aMissingOrShortSecretDisablesIssuingAndVerification() {
		var service = service("short");

		assertThrows(
			AuthUnavailableException.class,
			() -> service.issue("usr_a1", NOW));
		assertTrue(service.verify("header.payload.signature").isEmpty());
	}

	private static JwtAccessTokenService service(String secret) {
		return new JwtAccessTokenService(
			secret,
			"https://api.koready.cloud",
			"koready-api",
			Duration.ofMinutes(15),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}
}
