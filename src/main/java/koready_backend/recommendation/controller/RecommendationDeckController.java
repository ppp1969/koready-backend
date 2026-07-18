package koready_backend.recommendation.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.recommendation.application.RecommendationDeckService;
import koready_backend.recommendation.application.RecommendationEventService;

@Validated
@RestController
@RequestMapping("/api/v1/recommendation-decks")
public class RecommendationDeckController {

	private final RecommendationDeckService service;
	private final RecommendationEventService eventService;

	public RecommendationDeckController(
		RecommendationDeckService service,
		RecommendationEventService eventService
	) {
		this.service = service;
		this.eventService = eventService;
	}

	@PostMapping
	public ResponseEntity<ApiEnvelope<RecommendationDeckDtos.RecommendationDeckResponse>> create(
		@RequestBody @Valid RecommendationDeckDtos.CreateRecommendationDeckRequest body,
		@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false)
		String acceptLanguage,
		Authentication authentication,
		HttpServletRequest request
	) {
		var page = service.createDeck(
			authentication.getName(),
			body.scope(),
			body.originLocationId(),
			body.size(),
			PlaceLanguage.fromAcceptLanguage(acceptLanguage));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(
			"RECOMMENDATION_DECK_CREATED",
			RecommendationDeckDtos.from(page),
			TraceIdFilter.current(request)));
	}

	@PostMapping("/{deckId}/events")
	public ResponseEntity<ApiEnvelope<RecommendationDeckDtos.RecommendationEventResponse>>
		recordEvent(
			@PathVariable @NotBlank @Size(max = 100) String deckId,
			@RequestBody @Valid RecommendationDeckDtos.RecommendationEventRequest body,
			Authentication authentication,
			HttpServletRequest request
		) {
		var event = eventService.recordEvent(
			authentication.getName(),
			deckId,
			body.placeId(),
			body.eventType(),
			body.occurredAt());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(
			"RECOMMENDATION_EVENT_CREATED",
			RecommendationDeckDtos.from(event),
			TraceIdFilter.current(request)));
	}

	@GetMapping("/{deckId}")
	public ApiEnvelope<RecommendationDeckDtos.RecommendationDeckResponse> getPage(
		@PathVariable @NotBlank @Size(max = 100) String deckId,
		@RequestParam(required = false) @Size(max = 512) String cursor,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"RECOMMENDATION_DECK_OK",
			RecommendationDeckDtos.from(service.getPage(
				authentication.getName(), deckId, cursor)),
			TraceIdFilter.current(request));
	}
}
