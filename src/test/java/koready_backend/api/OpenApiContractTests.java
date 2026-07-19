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
		"GET /home",
		"GET /locations/search",
		"GET /users/me/locations",
		"POST /users/me/locations",
		"PUT /users/me/locations/{locationId}/default",
		"DELETE /users/me/locations/{locationId}",
		"PATCH /users/me/language",
		"GET /users/me/onboarding",
		"PUT /users/me/onboarding",
		"GET /monthly-recommendations",
		"POST /recommendation-decks",
		"GET /recommendation-decks/{deckId}",
		"POST /recommendation-decks/{deckId}/events",
		"GET /places",
		"GET /places/search",
		"GET /places/{placeId}",
		"GET /users/me/saved-places",
		"PUT /users/me/saved-places/{placeId}",
		"DELETE /users/me/saved-places/{placeId}",
		"GET /users/me/buddy-profile",
		"PUT /users/me/buddy-profile",
		"GET /buddy-profiles/{profileId}",
		"GET /message-threads",
		"POST /message-threads",
		"GET /message-threads/{threadId}",
		"POST /message-threads/{threadId}/messages",
		"PUT /message-threads/{threadId}/read",
		"POST /reports",
		"PUT /users/me/blocked-profiles/{profileId}",
		"DELETE /users/me/blocked-profiles/{profileId}",
		"GET /onboarding/place-candidate-sets/current",
		"GET /admin/onboarding/place-candidate-sets",
		"POST /admin/onboarding/place-candidate-sets",
		"GET /admin/onboarding/place-candidate-sets/{candidateSetId}",
		"PUT /admin/onboarding/place-candidate-sets/{candidateSetId}",
		"POST /admin/onboarding/place-candidate-sets/{candidateSetId}/publish",
		"POST /admin/onboarding/place-candidate-sets/{candidateSetId}/archive",
		"GET /admin/hori-tips",
		"POST /admin/hori-tips",
		"GET /admin/hori-tips/{horiTipId}",
		"PUT /admin/hori-tips/{horiTipId}",
		"PUT /admin/hori-tips/{horiTipId}/status",
		"GET /admin/open-api/summary",
		"GET /admin/open-api/calls",
		"GET /admin/open-api/calls/{callLogId}",
		"GET /admin/open-api/snapshots",
		"GET /admin/open-api/snapshots/{snapshotId}",
		"GET /admin/open-api/sync-cursors",
		"PUT /admin/open-api/sync-cursors/{cursorId}/enabled",
		"POST /admin/open-api/sync-cursors/{cursorId}/reset",
		"GET /admin/batch-jobs",
		"GET /admin/batch-jobs/{jobId}",
		"GET /admin/batch-jobs/{jobId}/items",
		"GET /admin/data-quality/summary");
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
	void bearerSecurityDocumentsTheLocalOnlyDevelopmentHarness() throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> components = asMap(contract.get("components"), "components");
		Map<String, Object> schemes = asMap(
			components.get("securitySchemes"), "securitySchemes");
		Map<String, Object> bearer = asMap(schemes.get("bearerAuth"), "bearerAuth");
		String description = String.valueOf(bearer.get("description"));

		assertTrue(description.contains("local 프로필"));
		assertTrue(description.contains("local-user"));
		assertTrue(description.contains("local-operator"));
		assertTrue(description.contains("local-auditor"));
		assertTrue(description.contains("local-admin"));
		assertTrue(description.contains("다른 프로필에서는 모두 401"));
	}

	@Test
	void syncCursorWritesDocumentAdminOnlyValidationAndAuditBehavior() throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> enabled = asMap(
			asMap(paths.get("/admin/open-api/sync-cursors/{cursorId}/enabled"),
				"enabled path").get("put"),
			"enabled operation");
		Map<String, Object> reset = asMap(
			asMap(paths.get("/admin/open-api/sync-cursors/{cursorId}/reset"),
				"reset path").get("post"),
			"reset operation");

		for (Map<String, Object> operation : List.of(enabled, reset)) {
			assertEquals(List.of("ADMIN"),
				asList(operation.get("x-required-roles"), "cursor write roles"));
			assertTrue(asMap(operation.get("responses"), "cursor write responses")
				.keySet().containsAll(Set.of("400", "401", "403", "404", "200")));
			String description = String.valueOf(operation.get("description"));
			assertTrue(description.contains("감사 로그"));
		}

		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> enabledRequest = asMap(
			schemas.get("EnabledRequest"), "EnabledRequest");
		assertEquals(List.of("enabled", "reason"),
			asList(enabledRequest.get("required"), "EnabledRequest.required"));
		assertTextBounds(enabledRequest, "reason", 1, 500);

		Map<String, Object> resetRequest = asMap(
			schemas.get("ResetCursorRequest"), "ResetCursorRequest");
		assertEquals(List.of("cursorValue", "reason"),
			asList(resetRequest.get("required"), "ResetCursorRequest.required"));
		assertTextBounds(resetRequest, "cursorValue", 1, 500);
		assertTextBounds(resetRequest, "reason", 1, 500);
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

	@Test
	void savedPlaceWritesRequireTheUiSource() throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> savedPlacePath = asMap(
			paths.get("/users/me/saved-places/{placeId}"),
			"/users/me/saved-places/{placeId}");
		Map<String, Object> saveOperation = asMap(
			savedPlacePath.get("put"), "PUT /users/me/saved-places/{placeId}");
		Map<String, Object> requestBody = asMap(
			saveOperation.get("requestBody"), "save place requestBody");
		assertEquals(Boolean.TRUE, requestBody.get("required"));
		Map<String, Object> content = asMap(requestBody.get("content"), "save place content");
		Map<String, Object> json = asMap(content.get("application/json"), "save place json");
		assertEquals(
			"#/components/schemas/SavePlaceRequest",
			asMap(json.get("schema"), "save place schema").get("$ref"));

		Map<String, Object> request = asMap(schemas.get("SavePlaceRequest"), "SavePlaceRequest");
		assertEquals(List.of("source"), asList(request.get("required"), "SavePlaceRequest.required"));
		Map<String, Object> source = asMap(
			asMap(request.get("properties"), "SavePlaceRequest.properties").get("source"),
			"SavePlaceRequest.source");
		assertEquals("#/components/schemas/SavedPlaceSource", source.get("$ref"));
		Map<String, Object> sourceEnum = asMap(
			schemas.get("SavedPlaceSource"), "SavedPlaceSource");
		assertEquals(
			List.of("HOME_MONTHLY", "RECOMMENDATION_CARD", "PLACE_DETAIL", "MAP"),
			asList(sourceEnum.get("enum"), "SavedPlaceSource.enum"));
	}

	@Test
	void languageUpdateDocumentsTheNonSkippingSignupTransition() throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> operation = asMap(
			asMap(paths.get("/users/me/language"), "/users/me/language").get("patch"),
			"PATCH /users/me/language");
		String description = String.valueOf(operation.get("description"));
		assertTrue(description.contains("NEED_TERMS"));
		assertTrue(description.contains("NEED_LANGUAGE"));
		assertTrue(description.contains("NEED_ONBOARDING"));
		assertTrue(description.contains("COMPLETED"));
		assertTrue(description.contains("nextStep=TERMS"));
		assertTrue(description.contains("nextStep=ONBOARDING"));

		Map<String, Object> responses = asMap(
			operation.get("responses"), "PATCH /users/me/language.responses");
		assertTrue(responses.containsKey("400"));
		assertTrue(responses.containsKey("401"));
		assertTrue(responses.containsKey("200"));
	}

	@Test
	void onboardingDocumentsRecoveryValidationAndIdempotentCompletion()
		throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> onboarding = asMap(
			paths.get("/users/me/onboarding"), "/users/me/onboarding");
		Map<String, Object> get = asMap(onboarding.get("get"), "onboarding get");
		Map<String, Object> put = asMap(onboarding.get("put"), "onboarding put");

		assertEquals("IMPLEMENTED", get.get("x-implementation-status"));
		assertEquals("IMPLEMENTED", put.get("x-implementation-status"));
		String getDescription = String.valueOf(get.get("description"));
		assertTrue(getDescription.contains("LOCATION"));
		assertTrue(getDescription.contains("TRAVEL_STYLES"));
		assertTrue(getDescription.contains("PREFERENCE_PLACES"));
		assertTrue(getDescription.contains("COMPLETED"));

		String putDescription = String.valueOf(put.get("description"));
		assertTrue(putDescription.contains("과거 발행"));
		assertTrue(putDescription.contains("발행된 적 없는 초안"));
		assertTrue(putDescription.contains("같은 본문"));
		assertTrue(putDescription.contains("preferenceTags"));
		assertTrue(asMap(put.get("responses"), "onboarding put responses")
			.keySet().containsAll(Set.of("400", "401", "409", "422", "200")));

		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> request = asMap(
			schemas.get("OnboardingRequest"), "OnboardingRequest");
		Map<String, Object> properties = asMap(
			request.get("properties"), "OnboardingRequest.properties");
		Map<String, Object> styles = asMap(properties.get("travelStyles"), "travelStyles");
		Map<String, Object> places = asMap(
			properties.get("selectedPreferencePlaceIds"), "selectedPreferencePlaceIds");
		assertEquals(1, styles.get("minItems"));
		assertEquals(4, styles.get("maxItems"));
		assertEquals(Boolean.TRUE, styles.get("uniqueItems"));
		assertEquals(1, places.get("minItems"));
		assertEquals(3, places.get("maxItems"));
		assertEquals(Boolean.TRUE, places.get("uniqueItems"));

		Map<String, Object> profile = asMap(
			schemas.get("OnboardingProfile"), "OnboardingProfile");
		Map<String, Object> tags = asMap(
			asMap(profile.get("properties"), "OnboardingProfile.properties")
				.get("preferenceTags"),
			"preferenceTags");
		assertEquals(0, tags.get("maxItems"));
	}

	@Test
	void recommendationEventsDocumentBestEffortTrackingAndServedCardOwnership()
		throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> operation = asMap(
			asMap(
				paths.get("/recommendation-decks/{deckId}/events"),
				"/recommendation-decks/{deckId}/events")
				.get("post"),
			"POST /recommendation-decks/{deckId}/events");
		String description = String.valueOf(operation.get("description"));
		assertTrue(description.contains("한 번만"));
		assertTrue(description.contains("자동 재시도"));
		assertTrue(description.contains("실제 저장 API 성공"));
		assertTrue(description.contains("재노출 제한"));
		assertTrue(description.contains("노출된 카드"));

		Map<String, Object> responses = asMap(
			operation.get("responses"), "recommendation event responses");
		assertTrue(responses.keySet().containsAll(Set.of("400", "401", "404", "201")));

		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> eventType = asMap(
			schemas.get("RecommendationEventType"), "RecommendationEventType");
		assertEquals(List.of(
			"CARD_EXPANDED",
			"CARD_PREVIOUS",
			"CARD_NEXT",
			"PLACE_DETAIL_CLICKED",
			"PLACE_SAVED",
			"PLACE_UNSAVED",
			"ROUTE_OPENED"),
			asList(eventType.get("enum"), "RecommendationEventType.enum"));
		assertFalse(asList(eventType.get("enum"), "RecommendationEventType.enum")
			.contains("CARD_SERVED"));

		Map<String, Object> request = asMap(
			schemas.get("RecommendationEventRequest"), "RecommendationEventRequest");
		Map<String, Object> properties = asMap(
			request.get("properties"), "RecommendationEventRequest.properties");
		assertEquals(1, asMap(properties.get("placeId"), "placeId").get("minimum"));
		String occurredAtDescription = String.valueOf(
			asMap(properties.get("occurredAt"), "occurredAt").get("description"));
		assertTrue(occurredAtDescription.contains("분석"));
		assertTrue(occurredAtDescription.contains("재노출 제한"));
	}

	@Test
	void adminHoriTipsDocumentRolesLifecycleAndAuditSubjects() throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> collection = asMap(
			paths.get("/admin/hori-tips"), "/admin/hori-tips");
		Map<String, Object> detail = asMap(
			paths.get("/admin/hori-tips/{horiTipId}"),
			"/admin/hori-tips/{horiTipId}");
		Map<String, Object> statusPath = asMap(
			paths.get("/admin/hori-tips/{horiTipId}/status"),
			"/admin/hori-tips/{horiTipId}/status");

		assertEquals(
			List.of("ADMIN", "OPERATOR", "AUDITOR"),
			asList(asMap(collection.get("get"), "hori list").get("x-required-roles"),
				"hori list roles"));
		assertEquals(
			List.of("ADMIN", "OPERATOR"),
			asList(asMap(collection.get("post"), "hori create").get("x-required-roles"),
				"hori create roles"));
		assertTrue(asMap(
			asMap(detail.get("put"), "hori update").get("responses"),
			"hori update responses").keySet().containsAll(Set.of(
				"400", "401", "403", "404", "409", "422", "200")));
		assertTrue(asMap(
			asMap(statusPath.get("put"), "hori status").get("responses"),
			"hori status responses").keySet().containsAll(Set.of(
				"400", "401", "403", "404", "409", "422", "200")));

		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> response = asMap(
			schemas.get("AdminHoriTipResponse"), "AdminHoriTipResponse");
		List<Object> required = asList(response.get("required"), "AdminHoriTipResponse.required");
		assertTrue(required.containsAll(List.of("createdBySubject", "updatedBySubject")));
		assertFalse(required.contains("createdByUserId"));
		Map<String, Object> properties = asMap(
			response.get("properties"), "AdminHoriTipResponse.properties");
		assertEquals("string", asMap(
			properties.get("createdBySubject"), "createdBySubject").get("type"));
		assertFalse(properties.containsKey("createdByUserId"));

		Map<String, Object> statuses = asMap(schemas.get("HoriTipStatus"), "HoriTipStatus");
		assertEquals(List.of("DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"),
			asList(statuses.get("enum"), "HoriTipStatus.enum"));
		Map<String, Object> targets = asMap(
			schemas.get("HoriTipStatusChangeTarget"), "HoriTipStatusChangeTarget");
		assertEquals(List.of("ACTIVE", "INACTIVE", "ARCHIVED"),
			asList(targets.get("enum"), "HoriTipStatusChangeTarget.enum"));
	}

	@Test
	void adminExternalApiReadsDocumentFiltersRedactionAndDeferredDownloads()
		throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		List<String> readPaths = List.of(
			"/admin/open-api/summary",
			"/admin/open-api/calls",
			"/admin/open-api/calls/{callLogId}",
			"/admin/open-api/snapshots",
			"/admin/open-api/snapshots/{snapshotId}");

		for (String path : readPaths) {
			Map<String, Object> operation = asMap(
				asMap(paths.get(path), path).get("get"), "GET " + path);
			assertEquals(
				List.of("ADMIN", "OPERATOR", "AUDITOR"),
				asList(operation.get("x-required-roles"), path + " roles"));
			assertEquals("IMPLEMENTED", operation.get("x-implementation-status"));
			Map<String, Object> responses = asMap(
				operation.get("responses"), path + " responses");
			assertTrue(responses.keySet().containsAll(Set.of("400", "401", "403", "200")));
		}

		Map<String, Object> calls = asMap(
			asMap(paths.get("/admin/open-api/calls"), "calls path").get("get"),
			"calls operation");
		Set<String> callParameterNames = directParameterNames(calls, "call parameters");
		assertTrue(callParameterNames.containsAll(Set.of(
			"apiName", "operation", "success", "httpStatus",
			"relatedJobId", "hasRawSnapshot")));

		Map<String, Object> snapshots = asMap(
			asMap(paths.get("/admin/open-api/snapshots"), "snapshots path").get("get"),
			"snapshots operation");
		assertTrue(directParameterNames(snapshots, "snapshot parameters")
			.containsAll(Set.of("operation", "retentionClass")));

		Map<String, Object> download = asMap(
			asMap(
				paths.get("/admin/open-api/snapshots/{snapshotId}/download-url"),
				"download path").get("post"),
			"download operation");
		assertEquals("PLANNED", download.get("x-implementation-status"));

		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> callSummary = asMap(
			schemas.get("OpenApiCallSummary"), "OpenApiCallSummary");
		assertFalse(asMap(callSummary.get("properties"), "call summary properties")
			.containsKey("endpoint"));
		Map<String, Object> callDetail = asMap(
			schemas.get("OpenApiCallResponse"), "OpenApiCallResponse");
		String callDetailText = String.valueOf(callDetail);
		assertFalse(callDetailText.contains("rawBody"));
		assertFalse(callDetailText.contains("responseBody"));

		Map<String, Object> rawStatus = asMap(
			schemas.get("RawSnapshotStatus"), "RawSnapshotStatus");
		assertEquals(
			List.of("NOT_APPLICABLE", "NOT_CAPTURED", "AVAILABLE", "EXPIRED"),
			asList(rawStatus.get("enum"), "RawSnapshotStatus.enum"));
		Map<String, Object> rawSnapshot = asMap(
			schemas.get("RawSnapshotResponse"), "RawSnapshotResponse");
		Map<String, Object> rawProperties = asMap(
			rawSnapshot.get("properties"), "RawSnapshotResponse.properties");
		assertEquals(
			List.of("JSON_GZIP", "XML_GZIP"),
			asList(asMap(rawProperties.get("storageFormat"), "storageFormat")
				.get("enum"), "storageFormat.enum"));
		assertEquals(
			List.of("COMPETITION_EVIDENCE", "DEBUG_TEMPORARY", "PROVIDER_RESTRICTED"),
			asList(asMap(rawProperties.get("retentionClass"), "retentionClass")
				.get("enum"), "retentionClass.enum"));
		assertEquals(
			List.of(Boolean.FALSE),
			asList(asMap(rawProperties.get("downloadable"), "downloadable")
				.get("enum"), "downloadable.enum"));
	}

	@Test
	void syncCursorOperationsMatchTheActualDatabaseSchemaAndRoles()
		throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> read = asMap(
			asMap(paths.get("/admin/open-api/sync-cursors"), "sync cursor path")
				.get("get"),
			"sync cursor read");

		assertEquals("IMPLEMENTED", read.get("x-implementation-status"));
		assertEquals(
			List.of("ADMIN", "OPERATOR", "AUDITOR"),
			asList(read.get("x-required-roles"), "sync cursor roles"));
		assertTrue(asMap(read.get("responses"), "sync cursor responses")
			.keySet().containsAll(Set.of("401", "403", "200")));

		Map<String, Object> enabled = asMap(
			asMap(
				paths.get("/admin/open-api/sync-cursors/{cursorId}/enabled"),
				"sync cursor enabled path").get("put"),
			"sync cursor enabled");
		Map<String, Object> reset = asMap(
			asMap(
				paths.get("/admin/open-api/sync-cursors/{cursorId}/reset"),
				"sync cursor reset path").get("post"),
			"sync cursor reset");
		assertEquals("IMPLEMENTED", enabled.get("x-implementation-status"));
		assertEquals("IMPLEMENTED", reset.get("x-implementation-status"));
		assertEquals(List.of("ADMIN"),
			asList(enabled.get("x-required-roles"), "sync cursor enabled roles"));
		assertEquals(List.of("ADMIN"),
			asList(reset.get("x-required-roles"), "sync cursor reset roles"));

		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> cursor = asMap(
			schemas.get("SyncCursorResponse"), "SyncCursorResponse");
		List<Object> required = asList(cursor.get("required"), "SyncCursorResponse.required");
		assertTrue(required.containsAll(List.of(
			"cursorId", "provider", "apiName", "operation", "cursorType",
			"enabled", "failureCount", "createdAt", "updatedAt")));
		assertFalse(required.contains("cursorValue"));
		Map<String, Object> properties = asMap(
			cursor.get("properties"), "SyncCursorResponse.properties");
		assertTrue(properties.keySet().containsAll(Set.of(
			"cursorId", "provider", "apiName", "operation", "cursorType",
			"cursorValue", "enabled", "failureCount", "lastSuccessAt",
			"lastFailureAt", "createdAt", "updatedAt")));
		assertFalse(properties.keySet().stream().anyMatch(Set.of(
			"lastErrorCode", "lastErrorMessage", "nextRunAt")::contains));
		assertEquals(
			List.of("MODIFIED_TIME", "PAGE", "DATE_RANGE", "MANUAL"),
			asList(asMap(schemas.get("SyncCursorType"), "SyncCursorType")
				.get("enum"), "SyncCursorType.enum"));
	}

	@Test
	void adminBatchReadsMatchTheActualDatabaseSchemaAndKeepWritesPlanned()
		throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		List<String> reads = List.of(
			"/admin/batch-jobs",
			"/admin/batch-jobs/{jobId}",
			"/admin/batch-jobs/{jobId}/items");

		for (String path : reads) {
			Map<String, Object> operation = asMap(
				asMap(paths.get(path), path).get("get"), "GET " + path);
			assertEquals("IMPLEMENTED", operation.get("x-implementation-status"));
			assertEquals(
				List.of("ADMIN", "OPERATOR", "AUDITOR"),
				asList(operation.get("x-required-roles"), path + " roles"));
			Map<String, Object> responses = asMap(
				operation.get("responses"), path + " responses");
			assertTrue(responses.keySet().containsAll(Set.of("400", "401", "403", "200")));
		}

		Map<String, Object> collection = asMap(
			paths.get("/admin/batch-jobs"), "batch collection");
		assertEquals(
			"PLANNED",
			asMap(collection.get("post"), "batch create").get("x-implementation-status"));
		assertTrue(directParameterNames(
			asMap(collection.get("get"), "batch list"), "batch list parameters")
			.containsAll(Set.of("jobType", "status", "triggerSource")));

		Map<String, Object> itemOperation = asMap(
			asMap(paths.get("/admin/batch-jobs/{jobId}/items"), "batch items").get("get"),
			"batch item list");
		assertTrue(directParameterNames(itemOperation, "batch item parameters")
			.containsAll(Set.of("status", "targetType")));
		Map<String, Object> retry = asMap(
			asMap(paths.get("/admin/batch-jobs/{jobId}/retry"), "batch retry").get("post"),
			"batch retry operation");
		assertEquals("PLANNED", retry.get("x-implementation-status"));

		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> job = asMap(schemas.get("BatchJobResponse"), "BatchJobResponse");
		List<Object> jobRequired = asList(job.get("required"), "BatchJobResponse.required");
		assertTrue(jobRequired.containsAll(List.of(
			"triggerSource", "message", "parameters", "updatedAt")));
		Map<String, Object> jobProperties = asMap(
			job.get("properties"), "BatchJobResponse.properties");
		assertFalse(jobProperties.containsKey("failureReason"));

		Map<String, Object> item = asMap(
			schemas.get("BatchJobItemResponse"), "BatchJobItemResponse");
		Map<String, Object> itemProperties = asMap(
			item.get("properties"), "BatchJobItemResponse.properties");
		assertTrue(itemProperties.keySet().containsAll(Set.of(
			"itemId", "targetType", "targetId", "status", "errorMessage",
			"createdAt", "updatedAt")));
		assertFalse(itemProperties.keySet().stream().anyMatch(Set.of(
			"operation", "attemptCount", "errorCode", "relatedCallLogId", "finishedAt")::contains));
		assertEquals(
			List.of("PENDING", "RUNNING", "COMPLETED", "FAILED"),
			asList(asMap(schemas.get("BatchItemStatus"), "BatchItemStatus")
				.get("enum"), "BatchItemStatus.enum"));
	}

	@Test
	void adminDataQualitySummaryDocumentsExactAggregationRules() throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> operation = asMap(
			asMap(paths.get("/admin/data-quality/summary"), "data quality path").get("get"),
			"data quality summary");

		assertEquals("IMPLEMENTED", operation.get("x-implementation-status"));
		assertEquals(
			List.of("ADMIN", "OPERATOR", "AUDITOR"),
			asList(operation.get("x-required-roles"), "data quality roles"));
		assertTrue(asMap(operation.get("responses"), "data quality responses")
			.keySet().containsAll(Set.of("401", "403", "200")));

		String description = String.valueOf(operation.get("description"));
		assertTrue(description.contains("active=true AND showFlag=true"));
		assertTrue(description.contains("KO 제목"));
		assertTrue(description.contains("외부 API를 호출하지"));
		assertFalse(description.contains("관광유형 매핑"));

		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> response = asMap(
			schemas.get("DataQualityResponse"), "DataQualityResponse");
		assertEquals(
			List.of("generatedAt", "places", "localization"),
			asList(response.get("required"), "DataQualityResponse.required"));

		Map<String, Object> places = asMap(
			schemas.get("PlaceQualitySummary"), "PlaceQualitySummary");
		Map<String, Object> placeProperties = asMap(
			places.get("properties"), "PlaceQualitySummary.properties");
		assertEquals(Set.of(
			"total", "active", "missingImage", "missingEnglish",
			"missingCoordinates", "missingAddress", "curationReady"),
			placeProperties.keySet());
		for (Object property : placeProperties.values()) {
			Map<String, Object> count = asMap(property, "place quality count");
			assertEquals("integer", count.get("type"));
			assertEquals("int64", count.get("format"));
			assertEquals(0, count.get("minimum"));
		}

		Map<String, Object> localization = asMap(
			schemas.get("LocalizationQualitySummary"), "LocalizationQualitySummary");
		Map<String, Object> localizationProperties = asMap(
			localization.get("properties"), "LocalizationQualitySummary.properties");
		assertEquals(Set.of("ktoEnglish", "aiTranslated", "manualEdited"),
			localizationProperties.keySet());
		for (Object property : localizationProperties.values()) {
			assertEquals("int64", asMap(property, "localization quality count").get("format"));
		}
	}

	@Test
	void buddyProfileOperationsAndValidationMatchTheImplementedContract()
		throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> path = asMap(
			paths.get("/users/me/buddy-profile"), "buddy profile path");
		Map<String, Object> read = asMap(path.get("get"), "buddy profile read");
		Map<String, Object> write = asMap(path.get("put"), "buddy profile write");

		assertEquals("IMPLEMENTED", read.get("x-implementation-status"));
		assertEquals("IMPLEMENTED", write.get("x-implementation-status"));
		assertTrue(asMap(read.get("responses"), "buddy read responses")
			.keySet().containsAll(Set.of("401", "200")));
		assertTrue(asMap(write.get("responses"), "buddy write responses")
			.keySet().containsAll(Set.of("400", "401", "200")));

		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> request = asMap(
			schemas.get("BuddyProfileRequest"), "BuddyProfileRequest");
		Map<String, Object> properties = asMap(
			request.get("properties"), "BuddyProfileRequest.properties");
		assertEquals(2048, asMap(properties.get("profileImageUrl"), "profileImageUrl")
			.get("maxLength"));
		assertEquals(30, asMap(properties.get("nickname"), "nickname").get("maxLength"));
		assertEquals(100, asMap(properties.get("nationality"), "nationality")
			.get("maxLength"));
		assertEquals(500, asMap(properties.get("bio"), "bio").get("maxLength"));

		Map<String, Object> languages = asMap(
			properties.get("availableLanguages"), "availableLanguages");
		assertEquals(1, languages.get("minItems"));
		assertEquals(2, languages.get("maxItems"));
		assertEquals(true, languages.get("uniqueItems"));
		Map<String, Object> styles = asMap(properties.get("buddyStyles"), "buddyStyles");
		assertEquals(6, styles.get("maxItems"));
		assertEquals(true, styles.get("uniqueItems"));
		assertEquals(20,
			asMap(properties.get("socialLinks"), "socialLinks").get("maxItems"));

		assertEquals(
			List.of("BEGINNER", "INTERMEDIATE", "ADVANCED"),
			asList(asMap(schemas.get("KoreanLevel"), "KoreanLevel").get("enum"),
				"KoreanLevel.enum"));
		assertEquals(
			List.of(
				"TRADITIONAL_CULTURE", "CAFE_TOUR", "FOODIE", "PHOTOGRAPHY",
				"HANOK_EXPERIENCE", "QUIET_TRAVEL"),
			asList(asMap(schemas.get("BuddyStyle"), "BuddyStyle").get("enum"),
				"BuddyStyle.enum"));
	}

	@Test
	void buddyBlockContractDocumentsIdempotencyAndSafetyErrors() throws IOException {
		Map<String, Object> paths = asMap(loadContract().get("paths"), "paths");
		Map<String, Object> blockPath = asMap(
			paths.get("/users/me/blocked-profiles/{profileId}"), "buddy block path");
		Map<String, Object> block = asMap(blockPath.get("put"), "buddy block operation");
		Map<String, Object> unblock = asMap(
			blockPath.get("delete"), "buddy unblock operation");

		for (Map<String, Object> operation : List.of(block, unblock)) {
			assertEquals("IMPLEMENTED", operation.get("x-implementation-status"));
			assertTrue(String.valueOf(operation.get("description")).contains("멱등"));
			assertTrue(asMap(operation.get("responses"), "buddy safety responses")
				.keySet().containsAll(Set.of("400", "401", "404")));
		}
		assertTrue(asMap(block.get("responses"), "block responses").containsKey("200"));
		assertTrue(asMap(unblock.get("responses"), "unblock responses")
			.containsKey("204"));
	}

	@Test
	void buddyMessageSendContractDocumentsImplementedSafetyAndIdempotency() throws IOException {
		Map<String, Object> paths = asMap(loadContract().get("paths"), "paths");
		Map<String, Object> threadPath = asMap(
			paths.get("/message-threads"), "message thread path");
		Map<String, Object> createThread = asMap(
			threadPath.get("post"), "create message thread operation");
		Map<String, Object> reply = asMap(asMap(
			paths.get("/message-threads/{threadId}/messages"),
			"message reply path").get("post"), "message reply operation");

		for (Map<String, Object> operation : List.of(createThread, reply)) {
			assertEquals("IMPLEMENTED", operation.get("x-implementation-status"));
			assertTrue(parameterReferences(operation, "message send parameters")
				.contains("#/components/parameters/IdempotencyKey"));
			assertTrue(asMap(operation.get("responses"), "message send responses")
				.keySet().containsAll(Set.of("400", "401", "404", "409", "422")));
			String description = String.valueOf(operation.get("description"));
			assertTrue(description.contains("1,000"));
			assertTrue(description.contains("멱등"));
		}
	}

	@Test
	void buddyMessageInboxContractDocumentsPaginationBlockingAndExplicitRead()
		throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> threadCollection = asMap(
			paths.get("/message-threads"), "message thread collection");
		Map<String, Object> list = asMap(
			threadCollection.get("get"), "message thread list");
		Map<String, Object> detail = asMap(asMap(
			paths.get("/message-threads/{threadId}"), "message thread detail path")
			.get("get"), "message thread detail");
		Map<String, Object> read = asMap(asMap(
			paths.get("/message-threads/{threadId}/read"), "message read path")
			.get("put"), "message read operation");

		for (Map<String, Object> operation : List.of(list, detail, read)) {
			assertEquals("IMPLEMENTED", operation.get("x-implementation-status"));
			assertTrue(asMap(operation.get("responses"), "message inbox responses")
				.keySet().containsAll(Set.of("400", "401", "422", "200")));
		}
		assertTrue(asMap(detail.get("responses"), "message detail responses")
			.containsKey("404"));
		assertTrue(asMap(read.get("responses"), "message read responses")
			.containsKey("404"));

		String listDescription = String.valueOf(list.get("description"));
		String detailDescription = String.valueOf(detail.get("description"));
		String readDescription = String.valueOf(read.get("description"));
		assertTrue(listDescription.contains("updatedAt DESC"));
		assertTrue(listDescription.contains("blocked=true"));
		assertTrue(detailDescription.contains("messageId ASC"));
		assertTrue(detailDescription.contains("읽음 처리하지"));
		assertTrue(readDescription.contains("멱등"));

		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> summary = asMap(
			schemas.get("MessageThreadSummary"), "MessageThreadSummary");
		assertTrue(asList(summary.get("required"), "MessageThreadSummary.required")
			.contains("blocked"));
		Map<String, Object> preview = asMap(asMap(
			summary.get("properties"), "MessageThreadSummary.properties")
			.get("preview"), "MessageThreadSummary.preview");
		assertEquals(100, preview.get("maxLength"));
	}

	@Test
	void buddyReportContractDocumentsIdempotencyOwnershipAndSeparateBlocking()
		throws IOException {
		Map<String, Object> contract = loadContract();
		Map<String, Object> paths = asMap(contract.get("paths"), "paths");
		Map<String, Object> report = asMap(asMap(
			paths.get("/reports"), "report path").get("post"), "report operation");

		assertEquals("IMPLEMENTED", report.get("x-implementation-status"));
		assertTrue(parameterReferences(report, "report parameters")
			.contains("#/components/parameters/IdempotencyKey"));
		assertTrue(asMap(report.get("responses"), "report responses")
			.keySet().containsAll(Set.of("400", "401", "404", "409", "422", "201")));
		String description = String.valueOf(report.get("description"));
		assertTrue(description.contains("수신한 메시지"));
		assertTrue(description.contains("Idempotency-Key"));
		assertTrue(description.contains("차단을 자동"));

		Map<String, Object> schemas = componentSchemas(contract);
		Map<String, Object> request = asMap(schemas.get("ReportRequest"), "ReportRequest");
		Map<String, Object> properties = asMap(
			request.get("properties"), "ReportRequest.properties");
		assertEquals(500, asMap(properties.get("reason"), "ReportRequest.reason")
			.get("maxLength"));
		assertEquals("^[1-9][0-9]*$",
			asMap(properties.get("targetId"), "ReportRequest.targetId").get("pattern"));
	}

	@Test
	void publicBuddyProfileDocumentsPrivacyAndCalculatedFields() throws IOException {
		Map<String, Object> paths = asMap(loadContract().get("paths"), "paths");
		Map<String, Object> operation = asMap(
			asMap(paths.get("/buddy-profiles/{profileId}"), "public buddy profile path")
				.get("get"),
			"public buddy profile operation");
		String description = String.valueOf(operation.get("description"));

		assertEquals("IMPLEMENTED", operation.get("x-implementation-status"));
		assertTrue(asMap(operation.get("responses"), "public buddy profile responses")
			.keySet().containsAll(Set.of("400", "401", "404", "200")));
		assertTrue(description.contains("profilePublic"));
		assertTrue(description.contains("snsPublic"));
		assertTrue(description.contains("blockedByMe"));
		assertTrue(description.contains("canMessage"));
	}

	private static Set<String> directParameterNames(
		Map<String, Object> operation,
		String location
	) {
		Set<String> names = new HashSet<>();
		for (Object value : asList(operation.get("parameters"), location)) {
			Map<String, Object> parameter = asMap(value, location + " item");
			if (parameter.get("name") instanceof String name) {
				names.add(name);
			}
		}
		return names;
	}

	private static Set<String> parameterReferences(
		Map<String, Object> operation,
		String location
	) {
		Set<String> references = new HashSet<>();
		for (Object value : asList(operation.get("parameters"), location)) {
			Map<String, Object> parameter = asMap(value, location + " item");
			if (parameter.get("$ref") instanceof String reference) {
				references.add(reference);
			}
		}
		return references;
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

	private static void assertTextBounds(
		Map<String, Object> schema,
		String property,
		int minimum,
		int maximum
	) {
		Map<String, Object> properties = asMap(
			schema.get("properties"), property + " properties");
		Map<String, Object> text = asMap(properties.get(property), property);
		assertEquals(minimum, text.get("minLength"));
		assertEquals(maximum, text.get("maxLength"));
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
