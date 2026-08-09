package koready_backend.api.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@RestController
public class PublishedOpenApiController {

	private static final Set<String> HTTP_METHODS = Set.of(
		"get", "post", "put", "patch", "delete", "options", "head", "trace");

	private final Map<String, Object> contract;

	public PublishedOpenApiController() {
		this.contract = loadContract();
		addDefaultUserRoles(contract);
	}

	@GetMapping(
		value = {"/v3/api-docs", "/v3/api-docs.yaml"},
		produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> getContract() {
		return contract;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> loadContract() {
		LoaderOptions options = new LoaderOptions();
		options.setMaxAliasesForCollections(100);
		Yaml yaml = new Yaml(new SafeConstructor(options));
		ClassPathResource resource = new ClassPathResource("static/openapi/koready.yaml");
		try (InputStream input = resource.getInputStream()) {
			return (Map<String, Object>)yaml.load(input);
		} catch (IOException exception) {
			throw new IllegalStateException("Canonical OpenAPI contract is unavailable", exception);
		}
	}

	@SuppressWarnings("unchecked")
	private static void addDefaultUserRoles(Map<String, Object> contract) {
		Object globalSecurity = contract.get("security");
		Map<String, Object> paths = (Map<String, Object>)contract.get("paths");
		for (Object pathValue : paths.values()) {
			Map<String, Object> path = (Map<String, Object>)pathValue;
			for (Map.Entry<String, Object> entry : path.entrySet()) {
				if (!HTTP_METHODS.contains(entry.getKey())) {
					continue;
				}
				Map<String, Object> operation = (Map<String, Object>)entry.getValue();
				Object security = operation.getOrDefault("security", globalSecurity);
				if (security instanceof List<?> requirements
					&& !requirements.isEmpty()
					&& !operation.containsKey("x-required-roles")) {
					operation.put("x-required-roles", List.of("USER"));
				}
			}
		}
	}
}
