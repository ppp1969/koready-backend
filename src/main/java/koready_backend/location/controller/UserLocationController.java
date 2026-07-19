package koready_backend.location.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.location.application.UserLocationService;

@Validated
@RestController
@RequestMapping("/api/v1/users/me/locations")
public class UserLocationController {

	private final UserLocationService service;

	public UserLocationController(UserLocationService service) {
		this.service = service;
	}

	@GetMapping
	public ApiEnvelope<UserLocationDtos.UserLocationListResponse> getAll(
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"USER_LOCATION_LIST_OK",
			UserLocationDtos.from(service.getAll(authentication.getName())),
			TraceIdFilter.current(request));
	}

	@PostMapping
	public ResponseEntity<ApiEnvelope<UserLocationDtos.UserLocationResponse>> create(
		@RequestBody @Valid UserLocationDtos.CreateLocationRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		var response = ApiEnvelope.success(
			"USER_LOCATION_CREATED",
			UserLocationDtos.from(service.create(authentication.getName(), body.toCommand())),
			TraceIdFilter.current(request));
		return ResponseEntity.status(201).body(response);
	}

	@PutMapping("/{locationId}/default")
	public ApiEnvelope<UserLocationDtos.UserLocationResponse> setDefault(
		@PathVariable @Positive long locationId,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"USER_LOCATION_DEFAULT_UPDATED",
			UserLocationDtos.from(
				service.setDefault(authentication.getName(), locationId)),
			TraceIdFilter.current(request));
	}

	@DeleteMapping("/{locationId}")
	public ResponseEntity<Void> delete(
		@PathVariable @Positive long locationId,
		Authentication authentication
	) {
		service.delete(authentication.getName(), locationId);
		return ResponseEntity.noContent().build();
	}
}
