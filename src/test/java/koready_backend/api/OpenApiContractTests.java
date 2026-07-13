package koready_backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class OpenApiContractTests {

	private static final Set<String> HTTP_METHODS = Set.of(
		"get", "post", "put", "patch", "delete", "options", "head", "trace");

	@Test
	void frontendContractIsCompleteAndInternallyConsistent() throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Set<String> operationIds = new HashSet<>();
		List<String> references = new ArrayList<>();
		int operationCount = 0;

		for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
			Map<String, Object> pathItem = asMap(pathEntry.getValue(), pathEntry.getKey());
			for (Map.Entry<String, Object> methodEntry : pathItem.entrySet()) {
				if (!HTTP_METHODS.contains(methodEntry.getKey())) {
					continue;
				}

				operationCount++;
				String location = methodEntry.getKey().toUpperCase() + " " + pathEntry.getKey();
				Map<String, Object> operation = asMap(methodEntry.getValue(), location);
				String operationId = String.valueOf(operation.get("operationId"));

				assertFalse(operationId.isBlank() || "null".equals(operationId),
					() -> location + " must define operationId");
				assertTrue(operationIds.add(operationId),
					() -> "Duplicate operationId: " + operationId);
				assertEquals("PLANNED", operation.get("x-implementation-status"),
					() -> location + " must expose its implementation status");

				Map<String, Object> responses = asMap(operation.get("responses"), location + " responses");
				Object security = operation.containsKey("security")
					? operation.get("security") : contract.get("security");
				if (security instanceof List<?> securityRequirements && !securityRequirements.isEmpty()) {
					assertTrue(responses.containsKey("401"),
						() -> location + " must document the common 401 response");
				}

				responses.forEach((status, responseValue) -> {
					if (status.matches("2\\d\\d") && !"204".equals(status)) {
						Map<String, Object> response = asMap(responseValue, location + " " + status);
						assertTrue(response.containsKey("$ref") || response.containsKey("content"),
							() -> location + " " + status + " must define a typed response");
					}
				});
			}
		}

		assertEquals(71, operationCount, "Unexpected API operation count");
		collectReferences(contract, references);
		for (String reference : references) {
			assertLocalReferenceResolves(contract, reference);
		}
	}

	private static Map<String, Object> loadContract() throws IOException {
		LoaderOptions options = new LoaderOptions();
		options.setAllowDuplicateKeys(false);
		Yaml yaml = new Yaml(new SafeConstructor(options));

		try (InputStream input = OpenApiContractTests.class.getClassLoader()
			.getResourceAsStream("static/openapi/koready.yaml")) {
			assertNotNull(input, "Processed OpenAPI contract is missing");
			return asMap(yaml.load(input), "OpenAPI root");
		}
	}

	private static void collectReferences(Object value, List<String> references) {
		if (value instanceof Map<?, ?> map) {
			Object reference = map.get("$ref");
			if (reference instanceof String referenceValue) {
				references.add(referenceValue);
			}
			map.values().forEach(child -> collectReferences(child, references));
		} else if (value instanceof List<?> list) {
			list.forEach(child -> collectReferences(child, references));
		}
	}

	private static void assertLocalReferenceResolves(Map<String, Object> root, String reference) {
		assertTrue(reference.startsWith("#/"), () -> "External $ref is not allowed: " + reference);
		Object current = root;

		for (String token : reference.substring(2).split("/")) {
			Map<String, Object> currentMap = asMap(current, reference);
			String decodedToken = token.replace("~1", "/").replace("~0", "~");
			assertTrue(currentMap.containsKey(decodedToken), () -> "Unresolved $ref: " + reference);
			current = currentMap.get(decodedToken);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value, String location) {
		assertTrue(value instanceof Map<?, ?>, () -> location + " must be an object");
		return (Map<String, Object>) value;
	}
}
