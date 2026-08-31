package koready_backend.place.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
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
import koready_backend.common.controller.AuthenticatedSubject;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.editorial.application.EditorialService;
import koready_backend.editorial.domain.EditorialLanguage;
import koready_backend.place.application.PlaceQueryService;
import koready_backend.place.application.port.ResponseLanguageResolver;
import koready_backend.place.domain.PlaceSort;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@Validated
@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

	private final PlaceQueryService placeQueryService;
	private final ResponseLanguageResolver languageResolver;
	private final EditorialService editorialService;

	public PlaceController(
		PlaceQueryService placeQueryService,
		ResponseLanguageResolver languageResolver,
		EditorialService editorialService
	) {
		this.placeQueryService = placeQueryService;
		this.languageResolver = languageResolver;
		this.editorialService = editorialService;
	}

	@GetMapping
	public ApiEnvelope<PlaceDtos.PlaceListResponse> getPlaces(
		@RequestParam ServiceRegionCode serviceRegionCode,
		@RequestParam(required = false) List<TravelStyle> travelStyles,
		@RequestParam(defaultValue = "RECOMMENDED") PlaceSort sort,
		@RequestParam(required = false) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
		@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
		Authentication authentication,
		HttpServletRequest request
	) {
		String userPublicId = AuthenticatedSubject.optional(authentication);
		var language = languageResolver.resolve(userPublicId, acceptLanguage);
		PlaceQueryService.PlacePage page = placeQueryService.getPlaces(
			serviceRegionCode,
			travelStyles,
			sort,
			cursor,
			size,
			language,
			userPublicId);
		var editorialContents = editorialService.findReadyCardContents(
			page.items().stream().map(PlaceQueryService.PlaceCard::placeId).toList(),
			EditorialLanguage.valueOf(language.name()));
		return ApiEnvelope.success(
			"PLACE_LIST_OK", PlaceDtos.from(page, editorialContents, language),
			TraceIdFilter.current(request));
	}

	@GetMapping("/search")
	public ApiEnvelope<PlaceDtos.PlaceListResponse> searchPlaces(
		@RequestParam @NotBlank @Size(max = 100) String query,
		@RequestParam(required = false) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
		@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
		Authentication authentication,
		HttpServletRequest request
	) {
		String userPublicId = AuthenticatedSubject.optional(authentication);
		var language = languageResolver.resolve(userPublicId, acceptLanguage);
		PlaceQueryService.PlacePage page = placeQueryService.search(
			query,
			cursor,
			size,
			language,
			userPublicId);
		var editorialContents = editorialService.findReadyCardContents(
			page.items().stream().map(PlaceQueryService.PlaceCard::placeId).toList(),
			EditorialLanguage.valueOf(language.name()));
		return ApiEnvelope.success(
			"PLACE_SEARCH_OK", PlaceDtos.from(page, editorialContents, language),
			TraceIdFilter.current(request));
	}

	@GetMapping("/{placeId}")
	public ApiEnvelope<PlaceDtos.PlaceDetailResponse> getPlace(
		@PathVariable @Positive long placeId,
		@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
		Authentication authentication,
		HttpServletRequest request
	) {
		String userPublicId = AuthenticatedSubject.optional(authentication);
		var language = languageResolver.resolve(userPublicId, acceptLanguage);
		PlaceQueryService.PlaceDetail detail = placeQueryService.getPlace(
			placeId,
			language,
			userPublicId);
		EditorialService.PublicEditorial editorial = editorialService.findOrEnqueue(
			placeId, EditorialLanguage.valueOf(language.name()), userPublicId);
		var relatedEditorialContents = editorialService.findReadyCardContents(
			detail.relatedPlaces().stream().map(PlaceQueryService.RelatedPlace::placeId).toList(),
			EditorialLanguage.valueOf(language.name()));
		return ApiEnvelope.success(
			"PLACE_DETAIL_OK",
			PlaceDtos.from(detail, editorial, relatedEditorialContents, language),
			TraceIdFilter.current(request));
	}
}
