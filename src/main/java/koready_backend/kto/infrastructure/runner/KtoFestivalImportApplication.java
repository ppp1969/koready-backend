package koready_backend.kto.infrastructure.runner;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import koready_backend.KoreadyBackendApplication;
import koready_backend.kto.application.KtoFestivalImportService;
import koready_backend.kto.application.model.KtoFestivalImportRequest;
import koready_backend.kto.application.model.KtoFestivalImportResult;

public final class KtoFestivalImportApplication {

	private static final Logger log = LoggerFactory.getLogger(KtoFestivalImportApplication.class);
	private static final DateTimeFormatter KTO_DATE = DateTimeFormatter.BASIC_ISO_DATE;
	private static final String PREFIX = "koready.kto.festival-import.";
	private static final String PROFILE_ARGUMENT = "--spring.profiles.active=";
	private static final Set<String> ALLOWED_PROFILES = Set.of("local", "staging");

	private KtoFestivalImportApplication() {
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
			Environment environment = context.getEnvironment();
			validateEnvironment(environment);
			KtoFestivalImportRequest request = request(environment);
			KtoFestivalImportResult result = context
				.getBean(KtoFestivalImportService.class)
				.importFestivals(request);
			log.info(
				"KTO festival import completed: eventStartDate={}, pages={}, items={}, "
					+ "replayedPages={}, lastPage={}, totalCount={}, truncated={}",
				KTO_DATE.format(result.eventStartDate()),
				result.processedPages(),
				result.processedItems(),
				result.replayedPages(),
				result.lastProcessedPage(),
				result.reportedTotalCount(),
				result.truncatedByPageLimit());
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
			throw new IllegalStateException("KTO festival import requires an explicit profile");
		}
		boolean invalid = Arrays.stream(requestedProfiles.split(","))
			.map(String::trim)
			.anyMatch(profile -> !ALLOWED_PROFILES.contains(profile));
		if (invalid) {
			throw new IllegalStateException("KTO festival import is limited to local or staging");
		}
	}

	private static void validateEnvironment(Environment environment) {
		if (!environment.getProperty(PREFIX + "confirm", Boolean.class, false)) {
			throw new IllegalStateException("KTO festival import requires explicit confirmation");
		}
		if (!environment.acceptsProfiles(Profiles.of("local", "staging"))
			|| environment.acceptsProfiles(Profiles.of("prod"))) {
			throw new IllegalStateException("KTO festival import is limited to local or staging");
		}
	}

	private static KtoFestivalImportRequest request(Environment environment) {
		String rawDate = environment.getRequiredProperty(PREFIX + "event-start-date");
		return new KtoFestivalImportRequest(
			LocalDate.parse(rawDate, KTO_DATE),
			environment.getProperty(PREFIX + "start-page", Integer.class, 1),
			environment.getProperty(PREFIX + "max-pages", Integer.class, 1));
	}
}
