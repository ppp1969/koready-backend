package koready_backend.kto.infrastructure.runner;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import koready_backend.KoreadyBackendApplication;
import koready_backend.kto.application.KtoCuratedPlaceImportService;
import koready_backend.onboarding.application.InitialCandidateSetBootstrapResult;
import koready_backend.onboarding.application.InitialCandidateSetBootstrapService;

public final class KtoCuratedOnboardingBootstrapApplication {

	private static final Logger log =
		LoggerFactory.getLogger(KtoCuratedOnboardingBootstrapApplication.class);
	private static final String CONFIRM_PROPERTY =
		"koready.kto.curated-bootstrap.confirm";
	private static final String SNAPSHOT_STORAGE_PROPERTY =
		"koready.kto.snapshot.storage";
	private static final String PROFILE_ARGUMENT = "--spring.profiles.active=";
	private static final Set<String> ALLOWED_PROFILES = Set.of("local", "staging");

	private KtoCuratedOnboardingBootstrapApplication() {
	}

	public static void main(String[] args) {
		validateRequestedProfile(
			args,
			System.getenv("SPRING_PROFILES_ACTIVE"),
			System.getProperty("spring.profiles.active"));
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
			KoreadyBackendApplication.class)
			.web(WebApplicationType.NONE)
			.properties(
				"springdoc.api-docs.enabled=false",
				"springdoc.swagger-ui.enabled=false")
			.run(args)) {
			validateEnvironment(context.getEnvironment());
			Map<String, Long> placeIds = context
				.getBean(KtoCuratedPlaceImportService.class)
				.importApprovedCatalog();
			InitialCandidateSetBootstrapResult result = context
				.getBean(InitialCandidateSetBootstrapService.class)
				.bootstrap(placeIds);
			log.info(
				"Curated onboarding bootstrap completed: places={}, candidateSetId={}, replayed={}",
				placeIds.size(),
				result.candidateSetId(),
				result.replayed());
		}
	}

	static void validateRequestedProfile(
		String[] args,
		String environmentProfiles,
		String systemProfiles
	) {
		String requestedProfiles = Arrays.stream(args)
			.filter(argument -> argument.startsWith(PROFILE_ARGUMENT))
			.map(argument -> argument.substring(PROFILE_ARGUMENT.length()))
			.findFirst()
			.orElse(systemProfiles == null ? environmentProfiles : systemProfiles);
		if (requestedProfiles == null || requestedProfiles.isBlank()) {
			throw new IllegalStateException(
				"Curated onboarding bootstrap requires an explicit profile");
		}
		boolean invalid = Arrays.stream(requestedProfiles.split(","))
			.map(String::trim)
			.anyMatch(profile -> !ALLOWED_PROFILES.contains(profile));
		if (invalid) {
			throw new IllegalStateException(
				"Curated onboarding bootstrap is limited to local or staging");
		}
	}

	static void validateEnvironment(Environment environment) {
		if (!environment.getProperty(CONFIRM_PROPERTY, Boolean.class, false)) {
			throw new IllegalStateException(
				"Curated onboarding bootstrap requires explicit confirmation");
		}
		if (!environment.acceptsProfiles(Profiles.of("local", "staging"))
			|| environment.acceptsProfiles(Profiles.of("prod"))) {
			throw new IllegalStateException(
				"Curated onboarding bootstrap is limited to local or staging");
		}
		if (environment.acceptsProfiles(Profiles.of("staging"))
			&& !"s3".equals(environment.getProperty(SNAPSHOT_STORAGE_PROPERTY))) {
			throw new IllegalStateException(
				"Staging curated onboarding bootstrap requires S3 snapshot storage");
		}
	}
}
