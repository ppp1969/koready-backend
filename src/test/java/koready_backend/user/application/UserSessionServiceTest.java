package koready_backend.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.exception.UserUnavailableException;
import koready_backend.user.application.port.UserSessionRepository;
import koready_backend.user.application.port.UserSessionRepository.UserSessionRecord;
import koready_backend.user.domain.NextStep;
import koready_backend.user.domain.SignupStatus;

class UserSessionServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
	private final UserSessionRepository repository = mock(UserSessionRepository.class);
	private final UserSessionService service = new UserSessionService(
		repository, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void returnsTheServerOwnedSessionState() {
		when(repository.find("usr_me", NOW)).thenReturn(Optional.of(new UserSessionRecord(
			7L, "usr_me", "me@example.com", null, PlaceLanguage.EN,
			SignupStatus.COMPLETED, 12L, true, true, 3L, false)));

		UserSessionService.UserSession result = service.get("usr_me");

		assertEquals(UserSessionService.PublicSignupStatus.ACTIVE, result.signupStatus());
		assertEquals(NextStep.COMPLETED, result.nextStep());
		assertEquals(12L, result.defaultLocationId());
		assertEquals(3L, result.unreadMessageCount());
	}

	@Test
	void preservesEveryIncompleteSignupStep() {
		assertStep(SignupStatus.NEED_TERMS,
			UserSessionService.PublicSignupStatus.TERMS_REQUIRED, NextStep.TERMS);
		assertStep(SignupStatus.NEED_LANGUAGE,
			UserSessionService.PublicSignupStatus.LANGUAGE_REQUIRED, NextStep.LANGUAGE);
		assertStep(SignupStatus.NEED_ONBOARDING,
			UserSessionService.PublicSignupStatus.ONBOARDING_REQUIRED, NextStep.ONBOARDING);
	}

	@Test
	void rejectsADeletedOrMissingAuthenticatedUser() {
		when(repository.find("usr_missing", NOW)).thenReturn(Optional.empty());

		assertThrows(UserUnavailableException.class, () -> service.get("usr_missing"));
	}

	private void assertStep(
		SignupStatus status,
		UserSessionService.PublicSignupStatus publicStatus,
		NextStep nextStep
	) {
		String publicId = "usr_" + status.name().toLowerCase();
		when(repository.find(publicId, NOW)).thenReturn(Optional.of(new UserSessionRecord(
			7L, publicId, "me@example.com", null, PlaceLanguage.KO,
			status, null, false, false, 0L, true)));

		UserSessionService.UserSession result = service.get(publicId);

		assertEquals(publicStatus, result.signupStatus());
		assertEquals(nextStep, result.nextStep());
	}
}
