package koready_backend.kto.infrastructure.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;

import koready_backend.kto.application.KtoClassificationDryRunService;
import koready_backend.kto.application.model.KtoClassificationDryRunReport;
import koready_backend.kto.infrastructure.persistence.JdbcKtoClassificationCandidateSource;
import tools.jackson.databind.json.JsonMapper;

public final class KtoClassificationDryRunApplication {

	private static final Logger log =
		LoggerFactory.getLogger(KtoClassificationDryRunApplication.class);
	private static final String PREFIX = "koready.kto.classification-dry-run.";
	private static final String PROFILE_ARGUMENT = "--spring.profiles.active=";
	private static final String DRY_RUN_PROFILE = "kto-classification-dry-run";
	private static final Set<String> ALLOWED_PROFILES = Set.of("local", "staging");

	private KtoClassificationDryRunApplication() {
	}

	public static void main(String[] args) {
		validateRequestedProfile(
			args,
			System.getenv("SPRING_PROFILES_ACTIVE"),
			System.getProperty("spring.profiles.active"));
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
			DryRunConfiguration.class)
			.profiles(DRY_RUN_PROFILE)
			.web(WebApplicationType.NONE)
			.properties(
				"spring.flyway.enabled=false",
				"spring.datasource.hikari.read-only=true",
				"spring.task.scheduling.enabled=false",
				"springdoc.api-docs.enabled=false",
				"springdoc.swagger-ui.enabled=false")
			.run(args)) {
			Environment environment = context.getEnvironment();
			int pageSize = environment.getProperty(PREFIX + "page-size", Integer.class, 500);
			int exampleLimit =
				environment.getProperty(PREFIX + "example-limit", Integer.class, 3);
			Path output = Path.of(environment.getProperty(
				PREFIX + "output",
				"build/reports/kto-classification-dry-run.json"))
				.toAbsolutePath()
				.normalize();

			KtoClassificationDryRunReport report = context
				.getBean(KtoClassificationDryRunService.class)
				.run(pageSize, exampleLimit);
			writeReport(context.getBean(JsonMapper.class), report, output);
			log.info(
				"KTO classification dry run completed: ruleVersion={}, totalPlaces={}, "
					+ "withImage={}, currentlyPublished={}, effectivelyClassified={}, "
					+ "effectivelyClassifiedWithImage={}, effectivelyClassifiedPublished={}, "
					+ "automaticUnclassified={}, multiStyle={}, output={}",
				report.ruleVersion(),
				report.totalPlaces(),
				report.placesWithImage(),
				report.currentlyPublishedPlaces(),
				report.effectivelyClassifiedPlaces(),
				report.effectivelyClassifiedPlacesWithImage(),
				report.effectivelyClassifiedCurrentlyPublishedPlaces(),
				report.automaticallyUnclassifiedPlaces(),
				report.multiStylePlaces(),
				output);
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
				"KTO classification dry run requires an explicit profile");
		}
		boolean invalid = Arrays.stream(requestedProfiles.split(","))
			.map(String::trim)
			.anyMatch(profile -> !ALLOWED_PROFILES.contains(profile));
		if (invalid) {
			throw new IllegalStateException(
				"KTO classification dry run is limited to local or staging");
		}
	}

	private static void writeReport(
		JsonMapper jsonMapper,
		KtoClassificationDryRunReport report,
		Path output
	) {
		try {
			Files.createDirectories(output.getParent());
			jsonMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
		} catch (IOException exception) {
			throw new IllegalStateException("KTO classification report could not be written", exception);
		}
	}

	@Configuration(proxyBeanMethods = false)
	@Profile(DRY_RUN_PROFILE)
	@EnableAutoConfiguration(exclude = FlywayAutoConfiguration.class)
	@Import({
		KtoClassificationDryRunService.class,
		JdbcKtoClassificationCandidateSource.class
	})
	static class DryRunConfiguration {
	}
}
