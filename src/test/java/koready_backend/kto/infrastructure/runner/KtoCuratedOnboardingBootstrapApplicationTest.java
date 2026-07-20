package koready_backend.kto.infrastructure.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

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

	@Test
	void rejectsLocalSnapshotsForTheStagingBootstrap() {
		MockEnvironment environment = new MockEnvironment()
			.withProperty("koready.kto.curated-bootstrap.confirm", "true")
			.withProperty("koready.kto.snapshot.storage", "local")
			.withProperty("spring.profiles.active", "staging");
		environment.setActiveProfiles("staging");

		assertThrows(IllegalStateException.class, () ->
			KtoCuratedOnboardingBootstrapApplication.validateEnvironment(environment));
	}

	@Test
	void permitsLocalSnapshotsForTheLocalBootstrapAndS3ForStaging() {
		MockEnvironment local = new MockEnvironment()
			.withProperty("koready.kto.curated-bootstrap.confirm", "true")
			.withProperty("koready.kto.snapshot.storage", "local");
		local.setActiveProfiles("local");
		assertDoesNotThrow(() -> KtoCuratedOnboardingBootstrapApplication.validateEnvironment(local));

		MockEnvironment staging = new MockEnvironment()
			.withProperty("koready.kto.curated-bootstrap.confirm", "true")
			.withProperty("koready.kto.snapshot.storage", "s3");
		staging.setActiveProfiles("staging");
		assertDoesNotThrow(() -> KtoCuratedOnboardingBootstrapApplication.validateEnvironment(staging));
	}
}
