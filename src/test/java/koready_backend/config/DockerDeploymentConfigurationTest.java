package koready_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class DockerDeploymentConfigurationTest {

	private static final Path COMPOSE_FILE = Path.of("compose.yml");
	private static final Path CI_WORKFLOW = Path.of(".github", "workflows", "ci.yml");
	private static final Path SMOKE_SCRIPT = Path.of("scripts", "smoke-docker.sh");

	@Test
	void dockerCiBootsTheApplicationWithTheRenderMemoryBoundary() throws IOException {
		Map<String, Object> compose = yaml(COMPOSE_FILE);
		Map<String, Object> services = map(compose.get("services"), "services");
		Map<String, Object> app = map(services.get("app"), "services.app");

		assertEquals("512m", app.get("mem_limit"));
		assertTrue(Files.isRegularFile(SMOKE_SCRIPT));

		String workflow = Files.readString(CI_WORKFLOW);
		assertTrue(workflow.contains("./scripts/smoke-docker.sh koready-backend:ci"));
	}

	private static Map<String, Object> yaml(Path path) throws IOException {
		LoaderOptions options = new LoaderOptions();
		options.setAllowDuplicateKeys(false);
		Yaml yaml = new Yaml(new SafeConstructor(options));
		return map(yaml.load(Files.readString(path)), path.toString());
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value, String location) {
		assertTrue(value instanceof Map<?, ?>, () -> location + " must be an object");
		return (Map<String, Object>) value;
	}
}
