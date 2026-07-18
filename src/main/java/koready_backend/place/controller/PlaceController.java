package koready_backend.place.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.place.application.PlaceQueryService;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.PlaceSort;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@Validated
@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

	private final PlaceQueryService placeQueryService;

	public PlaceController(PlaceQueryService placeQueryService) {
		this.placeQueryService = placeQueryService;
	}

	@GetMapping
	public ApiEnvelope<PlaceDtos.PlaceListResponse> getPlaces(
		@RequestParam ServiceRegionCode serviceRegionCode,
		@RequestParam(required = false) List<TravelStyle> travelStyles,
		@RequestParam(defaultValue = "RECOMMENDED") PlaceSort sort,
		@RequestParam(required = false) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
		@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
		HttpServletRequest request
	) {
		PlaceQueryService.PlacePage page = placeQueryService.getPlaces(
			serviceRegionCode,
			travelStyles,
			sort,
			cursor,
			size,
			PlaceLanguage.fromAcceptLanguage(acceptLanguage));
		return ApiEnvelope.success(
			"PLACE_LIST_OK", PlaceDtos.from(page), TraceIdFilter.current(request));
	}

	@GetMapping("/search")
	public ApiEnvelope<PlaceDtos.PlaceListResponse> searchPlaces(
		@RequestParam @NotBlank @Size(max = 100) String query,
		@RequestParam(required = false) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
		@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
		HttpServletRequest request
	) {
		PlaceQueryService.PlacePage page = placeQueryService.search(
			query,
			cursor,
			size,
			PlaceLanguage.fromAcceptLanguage(acceptLanguage));
		return ApiEnvelope.success(
			"PLACE_SEARCH_OK", PlaceDtos.from(page), TraceIdFilter.current(request));
	}

	@GetMapping("/{placeId}")
	public ApiEnvelope<PlaceDtos.PlaceDetailResponse> getPlace(
		@PathVariable @Positive long placeId,
		@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
		HttpServletRequest request
	) {
		PlaceQueryService.PlaceDetail detail = placeQueryService.getPlace(
			placeId, PlaceLanguage.fromAcceptLanguage(acceptLanguage));
		return ApiEnvelope.success(
			"PLACE_DETAIL_OK", PlaceDtos.from(detail), TraceIdFilter.current(request));
	}
}
