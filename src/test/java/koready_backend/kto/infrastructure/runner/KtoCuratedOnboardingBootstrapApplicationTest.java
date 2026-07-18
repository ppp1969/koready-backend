package koready_backend.kto.infrastructure.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KtoCuratedOnboardingBootstrapApplicationTest {

	@Test
	void allowsOnlyExplicitLocalOrStagingProfilesBeforeSpringStarts() {
		assertDoesNotThrow(() -> KtoCuratedOnboardingBootstrapApplication.validateRequestedProfile(
			new String[] {"--spring.profiles.active=local"}, null, null));
		assertDoesNotThrow(() -> KtoCuratedOnboardingBootstrapApplication.validateRequestedProfile(
			new String[] {"--spring.profiles.active=staging"}, null, null));

		assertThrows(IllegalStateException.class, () ->
			KtoCuratedOnboardingBootstrapApplication.validateRequestedProfile(
				new String[] {"--spring.profiles.active=prod"}, null, null));
		assertThrows(IllegalStateException.class, () ->
			KtoCuratedOnboardingBootstrapApplication.validateRequestedProfile(
				new String[0], "prod", null));
		assertThrows(IllegalStateException.class, () ->
			KtoCuratedOnboardingBootstrapApplication.validateRequestedProfile(
				new String[0], null, null));
	}
}
