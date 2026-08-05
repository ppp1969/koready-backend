package koready_backend.kto.infrastructure.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;

class KtoClassificationDryRunApplicationTest {

	@Test
	void acceptsExplicitLocalOrStagingProfile() {
		assertDoesNotThrow(() ->
			KtoClassificationDryRunApplication.validateRequestedProfile(
				new String[]{"--spring.profiles.active=local"},
				null,
				null));
		assertDoesNotThrow(() ->
			KtoClassificationDryRunApplication.validateRequestedProfile(
				new String[]{"--spring.profiles.active=staging"},
				null,
				null));
	}

	@Test
	void rejectsMissingOrProductionProfile() {
		assertThrows(
			IllegalStateException.class,
			() -> KtoClassificationDryRunApplication.validateRequestedProfile(
				new String[0],
				null,
				null));
		assertThrows(
			IllegalStateException.class,
			() -> KtoClassificationDryRunApplication.validateRequestedProfile(
				new String[]{"--spring.profiles.active=prod"},
				null,
				null));
	}

	@Test
	void excludesFlywayFromTheReadOnlyRunner() {
		EnableAutoConfiguration configuration =
			KtoClassificationDryRunApplication.DryRunConfiguration.class
				.getAnnotation(EnableAutoConfiguration.class);

		assertTrue(Arrays.asList(configuration.exclude()).contains(FlywayAutoConfiguration.class));
	}
}
