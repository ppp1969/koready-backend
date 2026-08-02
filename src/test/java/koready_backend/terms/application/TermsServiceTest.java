package koready_backend.terms.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import koready_backend.terms.application.TermsService.AgreementCommand;
import koready_backend.terms.application.exception.InvalidTermAgreementException;
import koready_backend.terms.application.exception.RequiredTermsNotAgreedException;
import koready_backend.terms.application.port.TermsRepository;
import koready_backend.terms.application.port.TermsRepository.AgreementChange;
import koready_backend.terms.application.port.TermsRepository.CurrentTerm;
import koready_backend.terms.application.port.TermsRepository.UserState;
import koready_backend.user.domain.NextStep;
import koready_backend.user.domain.SignupStatus;

class TermsServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-02T03:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
	private static final String USER_PUBLIC_ID = "usr_terms";

	private final TermsRepository repository = mock(TermsRepository.class);
	private final TermsService service = new TermsService(repository, CLOCK);

	@Test
	void returnsAnEmptySatisfiedListWhenNoTermsArePublished() {
		when(repository.findActiveUser(USER_PUBLIC_ID))
			.thenReturn(Optional.of(user(SignupStatus.NEED_TERMS)));
		when(repository.findCurrentTerms(7L, NOW)).thenReturn(List.of());

		var result = service.getRequiredTerms(USER_PUBLIC_ID);

		assertEquals(List.of(), result.terms());
		assertEquals(true, result.allRequiredAgreed());
	}

	@Test
	void advancesWithoutSeedDataWhenNoTermsArePublished() {
		when(repository.findActiveUserForUpdate(USER_PUBLIC_ID))
			.thenReturn(Optional.of(user(SignupStatus.NEED_TERMS)));
		when(repository.findCurrentTerms(7L, NOW)).thenReturn(List.of());

		var result = service.updateAgreements(USER_PUBLIC_ID, List.of());

		assertEquals(List.of(), result.agreements());
		assertEquals(true, result.allRequiredAgreed());
		assertEquals(NextStep.LANGUAGE, result.nextStep());
		verify(repository).updateSignupStatus(
			7L, SignupStatus.NEED_LANGUAGE, NOW);
	}

	@Test
	void rejectsMissingRequiredTermsWithoutChangingSignupState() {
		when(repository.findActiveUserForUpdate(USER_PUBLIC_ID))
			.thenReturn(Optional.of(user(SignupStatus.NEED_TERMS)));
		when(repository.findCurrentTerms(7L, NOW))
			.thenReturn(List.of(term(10L, true, false, null)));

		assertThrows(RequiredTermsNotAgreedException.class,
			() -> service.updateAgreements(USER_PUBLIC_ID, List.of()));

		verify(repository, never()).saveAgreements(
			org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyList(),
			org.mockito.ArgumentMatchers.any());
		verify(repository, never()).updateSignupStatus(
			org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	@Test
	void rejectsUnknownOrDuplicateVersionIds() {
		when(repository.findActiveUserForUpdate(USER_PUBLIC_ID))
			.thenReturn(Optional.of(user(SignupStatus.NEED_TERMS)));
		when(repository.findCurrentTerms(7L, NOW))
			.thenReturn(List.of(term(10L, true, false, null)));

		assertThrows(InvalidTermAgreementException.class,
			() -> service.updateAgreements(
				USER_PUBLIC_ID, List.of(new AgreementCommand(99L, true))));
		assertThrows(InvalidTermAgreementException.class,
			() -> service.updateAgreements(
				USER_PUBLIC_ID,
				List.of(
					new AgreementCommand(10L, true),
					new AgreementCommand(10L, true))));
	}

	@Test
	void persistsCurrentVersionsAndAdvancesToLanguageSelection() {
		CurrentTerm required = term(10L, true, false, null);
		CurrentTerm optional = new CurrentTerm(
			2L,
			20L,
			"MARKETING",
			"마케팅 정보 수신",
			false,
			"1.0",
			URI.create("https://koready.cloud/terms/marketing/1.0"),
			2,
			false,
			null);
		when(repository.findActiveUserForUpdate(USER_PUBLIC_ID))
			.thenReturn(Optional.of(user(SignupStatus.NEED_TERMS)));
		when(repository.findCurrentTerms(7L, NOW))
			.thenReturn(
				List.of(required, optional),
				List.of(
					required.withAgreement(true, NOW),
					optional.withAgreement(false, null)));

		var result = service.updateAgreements(
			USER_PUBLIC_ID,
			List.of(
				new AgreementCommand(10L, true),
				new AgreementCommand(20L, false)));

		verify(repository).saveAgreements(
			7L,
			List.of(
				new AgreementChange(10L, true),
				new AgreementChange(20L, false)),
			NOW);
		verify(repository).updateSignupStatus(
			7L, SignupStatus.NEED_LANGUAGE, NOW);
		assertEquals(NextStep.LANGUAGE, result.nextStep());
		assertEquals(true, result.agreements().getFirst().agreed());
		assertEquals(NOW, result.agreements().getFirst().agreedAt());
	}

	private static UserState user(SignupStatus status) {
		return new UserState(7L, status);
	}

	private static CurrentTerm term(
		long versionId,
		boolean required,
		boolean agreed,
		Instant agreedAt
	) {
		return new CurrentTerm(
			1L,
			versionId,
			"SERVICE_TERMS",
			"서비스 이용약관",
			required,
			"1.0",
			URI.create("https://koready.cloud/terms/service/1.0"),
			1,
			agreed,
			agreedAt);
	}
}
