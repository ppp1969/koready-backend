package koready_backend.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.exception.UserUnavailableException;
import koready_backend.user.application.port.UserLanguageRepository;
import koready_backend.user.application.port.UserLanguageRepository.UserLanguageState;
import koready_backend.user.domain.NextStep;
import koready_backend.user.domain.SignupStatus;

class UserLanguageServiceTest {

	private static final Instant BEFORE = Instant.parse("2026-07-19T00:00:00Z");
	private static final Instant NOW = Instant.parse("2026-07-19T03:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

	private final UserLanguageRepository repository = mock(UserLanguageRepository.class);
	private final UserLanguageService service = new UserLanguageService(repository, CLOCK);

	@Test
	void neverSkipsRequiredTermsWhenLanguageChanges() {
		UserLanguageState initial = state(PlaceLanguage.KO, SignupStatus.NEED_TERMS, BEFORE);
		UserLanguageState updated = state(PlaceLanguage.EN, SignupStatus.NEED_TERMS, NOW);
		when(repository.findByPublicIdForUpdate("usr_terms")).thenReturn(Optional.of(initial));
		when(repository.update(7L, PlaceLanguage.EN, SignupStatus.NEED_TERMS, NOW))
			.thenReturn(updated);

		UserLanguageService.LanguageResult result =
			service.update("usr_terms", PlaceLanguage.EN);

		assertEquals(PlaceLanguage.EN, result.language());
		assertEquals(NextStep.TERMS, result.nextStep());
		assertEquals(NOW, result.updatedAt());
	}

	@Test
	void advancesFromLanguageSelectionToOnboarding() {
		UserLanguageState initial = state(PlaceLanguage.KO, SignupStatus.NEED_LANGUAGE, BEFORE);
		UserLanguageState updated = state(
			PlaceLanguage.KO, SignupStatus.NEED_ONBOARDING, NOW);
		when(repository.findByPublicIdForUpdate("usr_language"))
			.thenReturn(Optional.of(initial));
		when(repository.update(7L, PlaceLanguage.KO, SignupStatus.NEED_ONBOARDING, NOW))
			.thenReturn(updated);

		UserLanguageService.LanguageResult result =
			service.update("usr_language", PlaceLanguage.KO);

		assertEquals(NextStep.ONBOARDING, result.nextStep());
		assertEquals(NOW, result.updatedAt());
	}

	@Test
	void keepsOnboardingAndCompletedStatesWhenLanguageChanges() {
		UserLanguageState onboarding = state(
			PlaceLanguage.KO, SignupStatus.NEED_ONBOARDING, BEFORE);
		when(repository.findByPublicIdForUpdate("usr_onboarding"))
			.thenReturn(Optional.of(onboarding));
		when(repository.update(7L, PlaceLanguage.EN, SignupStatus.NEED_ONBOARDING, NOW))
			.thenReturn(state(PlaceLanguage.EN, SignupStatus.NEED_ONBOARDING, NOW));

		assertEquals(
			NextStep.ONBOARDING,
			service.update("usr_onboarding", PlaceLanguage.EN).nextStep());

		UserLanguageState completed = state(
			PlaceLanguage.KO, SignupStatus.COMPLETED, BEFORE);
		when(repository.findByPublicIdForUpdate("usr_completed"))
			.thenReturn(Optional.of(completed));
		when(repository.update(7L, PlaceLanguage.EN, SignupStatus.COMPLETED, NOW))
			.thenReturn(state(PlaceLanguage.EN, SignupStatus.COMPLETED, NOW));

		assertEquals(
			NextStep.COMPLETED,
			service.update("usr_completed", PlaceLanguage.EN).nextStep());
	}

	@Test
	void preservesUpdatedAtForAnIdenticalCompletedSetting() {
		UserLanguageState completed = state(
			PlaceLanguage.EN, SignupStatus.COMPLETED, BEFORE);
		when(repository.findByPublicIdForUpdate("usr_completed"))
			.thenReturn(Optional.of(completed));

		UserLanguageService.LanguageResult result =
			service.update("usr_completed", PlaceLanguage.EN);

		assertEquals(BEFORE, result.updatedAt());
		assertEquals(NextStep.COMPLETED, result.nextStep());
		verify(repository, never()).update(
			7L, PlaceLanguage.EN, SignupStatus.COMPLETED, NOW);
	}

	@Test
	void rejectsADeletedOrMissingAuthenticatedUser() {
		when(repository.findByPublicIdForUpdate("usr_missing")).thenReturn(Optional.empty());

		assertThrows(UserUnavailableException.class,
			() -> service.update("usr_missing", PlaceLanguage.KO));
	}

	private static UserLanguageState state(
		PlaceLanguage language,
		SignupStatus status,
		Instant updatedAt
	) {
		return new UserLanguageState(7L, language, status, updatedAt);
	}
}
