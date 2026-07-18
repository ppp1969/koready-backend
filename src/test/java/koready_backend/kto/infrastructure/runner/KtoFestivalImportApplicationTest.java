package koready_backend.kto.infrastructure.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KtoFestivalImportApplicationTest {

	@Test
	void allowsOnlyExplicitLocalOrStagingProfilesBeforeSpringStarts() {
		assertDoesNotThrow(() -> KtoFestivalImportApplication.validateRequestedProfile(
			new String[] {"--spring.profiles.active=local"}, null, null));
		assertDoesNotThrow(() -> KtoFestivalImportApplication.validateRequestedProfile(
			new String[] {"--spring.profiles.active=staging"}, null, null));

		assertThrows(IllegalStateException.class, () ->
			KtoFestivalImportApplication.validateRequestedProfile(
				new String[] {"--spring.profiles.active=prod"}, null, null));
		assertThrows(IllegalStateException.class, () ->
			KtoFestivalImportApplication.validateRequestedProfile(
				new String[0], "prod", null));
		assertThrows(IllegalStateException.class, () ->
			KtoFestivalImportApplication.validateRequestedProfile(
				new String[0], null, null));
	}
}
