package koready_backend.location.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.location.application.LocationSearchService;

@Validated
@RestController
@RequestMapping("/api/v1/locations")
public class LocationSearchController {

	private final LocationSearchService service;

	public LocationSearchController(LocationSearchService service) {
		this.service = service;
	}

	@GetMapping("/search")
	public ApiEnvelope<LocationSearchDtos.LocationSearchResponse> search(
		@RequestParam @NotBlank @Size(max = 100) String query,
		@RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"LOCATION_SEARCH_OK",
			LocationSearchDtos.from(service.search(query, limit)),
			TraceIdFilter.current(request));
	}
}
