package koready_backend.place.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.place.application.SavedPlaceService;
import koready_backend.place.domain.PlaceLanguage;

@Validated
@RestController
@RequestMapping("/api/v1/users/me/saved-places")
public class SavedPlaceController {

	private final SavedPlaceService service;

	public SavedPlaceController(SavedPlaceService service) {
		this.service = service;
	}

	@GetMapping
	public ApiEnvelope<SavedPlaceDtos.SavedPlaceListResponse> getSavedPlaces(
		@RequestParam(required = false) @Size(max = 512) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
		@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false)
		String acceptLanguage,
		Authentication authentication,
		HttpServletRequest request
	) {
		var page = service.getSavedPlaces(
			authentication.getName(),
			cursor,
			size,
			PlaceLanguage.fromAcceptLanguage(acceptLanguage));
		return ApiEnvelope.success(
			"SAVED_PLACE_LIST_OK",
			SavedPlaceDtos.from(page),
			TraceIdFilter.current(request));
	}

	@PutMapping("/{placeId}")
	public ApiEnvelope<SavedPlaceDtos.SavePlaceResponse> savePlace(
		@PathVariable @Positive long placeId,
		@RequestBody @Valid SavedPlaceDtos.SavePlaceRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		var result = service.save(authentication.getName(), placeId, body.source());
		return ApiEnvelope.success(
			"PLACE_SAVED",
			SavedPlaceDtos.from(result),
			TraceIdFilter.current(request));
	}

	@DeleteMapping("/{placeId}")
	public ResponseEntity<Void> unsavePlace(
		@PathVariable @Positive long placeId,
		Authentication authentication
	) {
		service.unsave(authentication.getName(), placeId);
		return ResponseEntity.noContent().build();
	}
}
