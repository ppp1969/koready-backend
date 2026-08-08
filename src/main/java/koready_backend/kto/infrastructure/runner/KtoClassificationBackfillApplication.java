package koready_backend.kto.infrastructure.runner;

import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import koready_backend.kto.application.KtoClassificationBackfillService;
import koready_backend.kto.application.model.KtoClassificationApplyResult;
import koready_backend.kto.infrastructure.persistence.JdbcKtoClassificationBackfillStore;
import koready_backend.kto.infrastructure.persistence.JdbcKtoClassificationCandidateSource;

public final class KtoClassificationBackfillApplication {

	private static final Logger log =
		LoggerFactory.getLogger(KtoClassificationBackfillApplication.class);
	private static final String PREFIX = "koready.kto.classification-backfill.";
	private static final String PROFILE_ARGUMENT = "--spring.profiles.active=";
	private static final String RUNNER_PROFILE = "kto-classification-backfill";
	private static final Set<String> ALLOWED_PROFILES = Set.of("local", "staging", "prod");

	private KtoClassificationBackfillApplication() {
	}

	public static void main(String[] args) {
		validateRequestedProfile(
			args,
			System.getenv("SPRING_PROFILES_ACTIVE"),
			System.getProperty("spring.profiles.active"));
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
			BackfillConfiguration.class)
			.profiles(RUNNER_PROFILE)
			.web(WebApplicationType.NONE)
			.properties(
				"spring.task.scheduling.enabled=false",
				"springdoc.api-docs.enabled=false",
				"springdoc.swagger-ui.enabled=false")
			.run(args)) {
			Environment environment = context.getEnvironment();
			validateConfirmation(environment.getProperty(PREFIX + "confirm", Boolean.class, false));
			int pageSize = environment.getProperty(PREFIX + "page-size", Integer.class, 500);
			boolean reset = environment.getProperty(PREFIX + "reset", Boolean.class, false);

			KtoClassificationApplyResult result = context
				.getBean(KtoClassificationBackfillService.class)
				.run(pageSize, reset);
			log.info(
				"KTO classification backfill completed: ruleVersion={}, processed={}, "
					+ "classified={}, unclassified={}, mappings={}, lastPlaceId={}, completed={}",
				result.ruleVersion(),
				result.processedPlaces(),
				result.classifiedPlaces(),
				result.unclassifiedPlaces(),
				result.automaticMappings(),
				result.lastPlaceId(),
				result.completed());
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
				"KTO classification backfill requires an explicit profile");
		}
		boolean invalid = Arrays.stream(requestedProfiles.split(","))
			.map(String::trim)
			.anyMatch(profile -> !ALLOWED_PROFILES.contains(profile));
		if (invalid) {
			throw new IllegalStateException(
				"KTO classification backfill profile must be local, staging, or prod");
		}
	}

	static void validateConfirmation(boolean confirmed) {
		if (!confirmed) {
			throw new IllegalStateException(
				"KTO classification backfill requires explicit confirmation");
		}
	}

	@Configuration(proxyBeanMethods = false)
	@Profile(RUNNER_PROFILE)
	@EnableAutoConfiguration
	@Import({
		KtoClassificationBackfillService.class,
		JdbcKtoClassificationCandidateSource.class,
		JdbcKtoClassificationBackfillStore.class
	})
	static class BackfillConfiguration {
	}
}
