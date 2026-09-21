package koready_backend.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SignupStatusTest {
	@Test
	void requiresLanguageThenTermsThenOnboarding() {
		SignupStatus state = SignupStatus.NEED_LANGUAGE;
		assertEquals(NextStep.LANGUAGE, state.nextStep());
		state = state.afterLanguageSelection();
		assertEquals(NextStep.TERMS, state.nextStep());
		state = state.afterTermsAgreement();
		assertEquals(NextStep.ONBOARDING, state.nextStep());
	}

	@Test
	void earlyTermsCannotSkipLanguageAndRepeatedLanguageCannotSkipTerms() {
		assertEquals(SignupStatus.NEED_LANGUAGE, SignupStatus.NEED_LANGUAGE.afterTermsAgreement());
		assertEquals(SignupStatus.NEED_TERMS, SignupStatus.NEED_TERMS.afterLanguageSelection());
	}

	@Test
	void laterStepsDoNotRegress() {
		for (var state : new SignupStatus[]{SignupStatus.NEED_ONBOARDING, SignupStatus.COMPLETED}) {
			assertEquals(state, state.afterLanguageSelection());
			assertEquals(state, state.afterTermsAgreement());
		}
	}
}
