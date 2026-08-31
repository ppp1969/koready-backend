package koready_backend.route.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.route.application.RouteService;

@Validated
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

	private final RouteService service;

	public RouteController(RouteService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<ApiEnvelope<RouteDtos.RouteResponse>> create(
		@RequestBody @Valid RouteDtos.RouteRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		var data = RouteDtos.from(service.create(authentication.getName(), body.toCommand()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(
			"ROUTE_CREATED", data, TraceIdFilter.current(request)));
	}

	@GetMapping("/{routeId}")
	public ApiEnvelope<RouteDtos.RouteResponse> get(
		@PathVariable @Pattern(regexp = "route_[0-9a-f]{32}") String routeId,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"ROUTE_OK",
			RouteDtos.from(service.get(authentication.getName(), routeId)),
			TraceIdFilter.current(request));
	}
}
