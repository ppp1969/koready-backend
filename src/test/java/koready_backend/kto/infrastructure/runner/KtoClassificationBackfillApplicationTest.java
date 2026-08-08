package koready_backend.kto.infrastructure.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KtoClassificationBackfillApplicationTest {

	@Test
	void acceptsOnlyExplicitDatabaseProfiles() {
		for (String profile : new String[]{"local", "staging", "prod"}) {
			assertDoesNotThrow(() ->
				KtoClassificationBackfillApplication.validateRequestedProfile(
					new String[]{"--spring.profiles.active=" + profile},
					null,
					null));
		}
		assertThrows(
			IllegalStateException.class,
			() -> KtoClassificationBackfillApplication.validateRequestedProfile(
				new String[0],
				null,
				null));
		assertThrows(
			IllegalStateException.class,
			() -> KtoClassificationBackfillApplication.validateRequestedProfile(
				new String[]{"--spring.profiles.active=test"},
				null,
				null));
	}

	@Test
	void requiresAnExplicitWriteConfirmation() {
		assertDoesNotThrow(() ->
			KtoClassificationBackfillApplication.validateConfirmation(true));
		assertThrows(
			IllegalStateException.class,
			() -> KtoClassificationBackfillApplication.validateConfirmation(false));
	}
}
