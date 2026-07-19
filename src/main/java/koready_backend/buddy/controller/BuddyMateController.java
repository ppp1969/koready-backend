package koready_backend.buddy.controller;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.buddy.application.BuddyMateService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@Validated
@RestController
@RequestMapping("/api/v1/places/{placeId}/mates")
public class BuddyMateController {

	private final BuddyMateService service;

	public BuddyMateController(BuddyMateService service) {
		this.service = service;
	}

	@GetMapping
	public ApiEnvelope<PlaceMateDtos.PlaceMateListResponse> getMates(
		@PathVariable @Positive long placeId,
		@RequestParam(required = false) @Size(max = 512) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"PLACE_MATE_LIST_OK",
			PlaceMateDtos.from(service.getMates(
				authentication.getName(), placeId, cursor, size)),
			TraceIdFilter.current(request));
	}
}
