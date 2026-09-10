package koready_backend.buddy.controller;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.buddy.application.BuddyMateService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.place.application.port.ResponseLanguageResolver;

@Validated
@RestController
@RequestMapping("/api/v1/places/{placeId}/mates")
public class BuddyMateController {

	private final BuddyMateService service;
	private final ResponseLanguageResolver languageResolver;

	public BuddyMateController(
		BuddyMateService service,
		ResponseLanguageResolver languageResolver
	) {
		this.service = service;
		this.languageResolver = languageResolver;
	}

	@GetMapping
	public ApiEnvelope<PlaceMateDtos.PlaceMateListResponse> getMates(
		@PathVariable @Positive long placeId,
		@RequestParam(required = false) @Size(max = 512) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
		Authentication authentication,
		@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"PLACE_MATE_LIST_OK",
			PlaceMateDtos.from(service.getMates(
				authentication.getName(), placeId, cursor, size),
				languageResolver.resolve(authentication.getName(), acceptLanguage)),
			TraceIdFilter.current(request));
	}
}
