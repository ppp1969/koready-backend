package koready_backend.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class AwsElasticBeanstalkDeploymentConfigurationTest {

	private static final Path HEALTHCHECK_CONFIGURATION = Path.of(
		".ebextensions",
		"01-healthcheck.config"
	);
	private static final Path EB_IGNORE = Path.of(".ebignore");

	@Test
	void configuresReadinessEndpointForElasticBeanstalkHealthChecks() throws IOException {
		Map<String, Object> configuration = new Yaml().load(
			Files.readString(HEALTHCHECK_CONFIGURATION)
		);
		Map<String, Object> optionSettings = map(configuration.get("option_settings"));
		Map<String, Object> applicationSetting = map(
			optionSettings.get("aws:elasticbeanstalk:application")
		);

		assertEquals(
			"/actuator/health/readiness",
			applicationSetting.get("Application Healthcheck URL")
		);
	}

	@Test
	void excludesLocalSecretsAndBuildOutputFromElasticBeanstalkSourceBundle() throws IOException {
		String ignoreRules = Files.readString(EB_IGNORE);

		assertTrue(ignoreRules.contains(".env"));
		assertTrue(ignoreRules.contains(".env.*"));
		assertTrue(ignoreRules.contains("!.env.example"));
		assertTrue(ignoreRules.contains("*.pem"));
		assertTrue(ignoreRules.contains("*.key"));
		assertTrue(ignoreRules.contains(".gradle"));
		assertTrue(ignoreRules.contains("build"));
		assertFalse(ignoreRules.contains("src"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}
}
