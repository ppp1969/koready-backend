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
	private static final Set<String> IMPLEMENTED_OPERATIONS = Set.of(
		"GET /monthly-recommendations",
		"GET /places",
		"GET /places/search",
		"GET /places/{placeId}",
		"GET /onboarding/place-candidate-sets/current",
		"GET /admin/onboarding/place-candidate-sets",
		"POST /admin/onboarding/place-candidate-sets",
		"GET /admin/onboarding/place-candidate-sets/{candidateSetId}",
		"PUT /admin/onboarding/place-candidate-sets/{candidateSetId}",
		"POST /admin/onboarding/place-candidate-sets/{candidateSetId}/publish",
		"POST /admin/onboarding/place-candidate-sets/{candidateSetId}/archive");
	private static final Set<String> ANONYMOUS_IMPLEMENTED_OPERATIONS = Set.of(
		"GET /monthly-recommendations",
		"GET /places",
		"GET /places/search",
		"GET /places/{placeId}");

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
				String expectedStatus = IMPLEMENTED_OPERATIONS.contains(location)
					? "IMPLEMENTED" : "PLANNED";
				assertEquals(expectedStatus, operation.get("x-implementation-status"),
					() -> location + " must expose its actual implementation status");

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

	@Test
	void implementedAnonymousReadsAreExplicitlyAnonymous() throws IOException {
		Map<String, Object> paths = asMap(loadContract().get("paths"), "paths");

		for (String location : ANONYMOUS_IMPLEMENTED_OPERATIONS) {
			String path = location.substring("GET ".length());
			Map<String, Object> operation = asMap(
				asMap(paths.get(path), path).get("get"), location);
			assertEquals(List.of(), asList(operation.get("security"), location + ".security"));
			assertFalse(asMap(operation.get("responses"), location + ".responses")
				.containsKey("401"));
		}
	}

	@Test
	void onboardingDoesNotCollectTravelPurpose() throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> schemas = componentSchemas(contract);

		assertFalse(schemas.containsKey("TravelPurpose"),
			"방문 목적은 온보딩에서 삭제되어 schema로도 노출하면 안 된다");

		Map<String, Object> request = asMap(schemas.get("OnboardingRequest"), "OnboardingRequest");
		assertEquals(List.of(
			"currentLocationId", "travelStyles", "candidateSetId", "candidateSetVersion",
			"selectedPreferencePlaceIds"), asList(request.get("required"), "OnboardingRequest.required"));
		assertFalse(asMap(request.get("properties"), "OnboardingRequest.properties")
			.containsKey("travelPurpose"));

		Map<String, Object> progress = asMap(
			schemas.get("OnboardingProgressResponse"), "OnboardingProgressResponse");
		assertFalse(asMap(progress.get("properties"), "OnboardingProgressResponse.properties")
			.containsKey("travelPurpose"));

		Map<String, Object> profile = asMap(schemas.get("OnboardingProfile"), "OnboardingProfile");
		assertFalse(asList(profile.get("required"), "OnboardingProfile.required")
			.contains("travelPurpose"));
		assertFalse(asMap(profile.get("properties"), "OnboardingProfile.properties")
			.containsKey("travelPurpose"));

		Map<String, Object> onboardingStep = asMap(schemas.get("OnboardingStep"), "OnboardingStep");
		assertEquals(List.of("LOCATION", "TRAVEL_STYLES", "PREFERENCE_PLACES", "COMPLETED"),
			asList(onboardingStep.get("enum"), "OnboardingStep.enum"));
	}

	@Test
	void monthlyRecommendationsDistinguishFestivalYearOccurrenceAndStatus() throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> monthlyPath = asMap(
			paths.get("/monthly-recommendations"), "/monthly-recommendations");
		Map<String, Object> monthlyGet = asMap(monthlyPath.get("get"), "GET /monthly-recommendations");

		Map<String, Object> yearParameter = asList(
			monthlyGet.get("parameters"), "GET /monthly-recommendations.parameters").stream()
			.filter(Map.class::isInstance)
			.map(value -> asMap(value, "monthly parameter"))
			.filter(value -> "year".equals(value.get("name")))
			.findFirst()
			.orElseThrow(() -> new AssertionError("월별 추천은 required year query가 필요하다"));
		assertEquals(Boolean.TRUE, yearParameter.get("required"));
		Map<String, Object> yearSchema = asMap(yearParameter.get("schema"), "year schema");
		assertEquals(2000, yearSchema.get("minimum"));
		assertEquals(2100, yearSchema.get("maximum"));

		Map<String, Object> status = asMap(
			schemas.get("FestivalOccurrenceStatus"), "FestivalOccurrenceStatus");
		assertEquals(List.of("UPCOMING", "ONGOING", "ENDED"),
			asList(status.get("enum"), "FestivalOccurrenceStatus.enum"));

		Map<String, Object> occurrence = asMap(
			schemas.get("FestivalOccurrenceSummary"), "FestivalOccurrenceSummary");
		assertEquals(List.of(
			"occurrenceId", "eventYear", "startDate", "endDate", "status", "dateRangeText"),
			asList(occurrence.get("required"), "FestivalOccurrenceSummary.required"));

		Map<String, Object> placeCard = asMap(schemas.get("PlaceCard"), "PlaceCard");
		Map<String, Object> placeCardProperties = asMap(placeCard.get("properties"), "PlaceCard.properties");
		assertTrue(placeCardProperties.containsKey("festivalOccurrence"));
		assertFalse(placeCardProperties.containsKey("startDate"));
		assertFalse(placeCardProperties.containsKey("endDate"));

		assertRequiredContains(schemas, "MonthlyRecommendationPreview", "year");
		assertRequiredContains(schemas, "MonthlyRecommendationListResponse", "year");
		assertRequiredContains(schemas, "MonthlyRecommendationFilters", "year");
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

	private static Map<String, Object> componentSchemas(Map<String, Object> contract) {
		Map<String, Object> components = asMap(contract.get("components"), "components");
		return asMap(components.get("schemas"), "components.schemas");
	}

	private static void assertRequiredContains(
		Map<String, Object> schemas, String schemaName, String requiredProperty) {
		Map<String, Object> schema = asMap(schemas.get(schemaName), schemaName);
		assertTrue(asList(schema.get("required"), schemaName + ".required").contains(requiredProperty),
			() -> schemaName + " must require " + requiredProperty);
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

	@SuppressWarnings("unchecked")
	private static List<Object> asList(Object value, String location) {
		assertTrue(value instanceof List<?>, () -> location + " must be an array");
		return (List<Object>) value;
	}
}
