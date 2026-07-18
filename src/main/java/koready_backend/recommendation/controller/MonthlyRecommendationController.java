package koready_backend.recommendation.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.MonthlyRecommendationService;
import koready_backend.recommendation.domain.DateFilterType;
import koready_backend.recommendation.domain.RecommendationSort;

@Validated
@RestController
@RequestMapping("/api/v1/monthly-recommendations")
public class MonthlyRecommendationController {

	private final MonthlyRecommendationService service;

	public MonthlyRecommendationController(MonthlyRecommendationService service) {
		this.service = service;
	}

	@GetMapping
	public ApiEnvelope<MonthlyRecommendationDtos.MonthlyRecommendationListResponse>
		getMonthlyRecommendations(
			@RequestParam @Min(2000) @Max(2100) int year,
			@RequestParam @Min(1) @Max(12) int month,
			@RequestParam(required = false) ServiceRegionCode serviceRegionCode,
			@RequestParam(defaultValue = "ALL") DateFilterType dateFilterType,
			@RequestParam(required = false)
			@DateTimeFormat(iso = ISO.DATE) LocalDate customStartDate,
			@RequestParam(required = false)
			@DateTimeFormat(iso = ISO.DATE) LocalDate customEndDate,
			@RequestParam(required = false) List<TravelStyle> travelStyles,
			@RequestParam(defaultValue = "RECOMMENDED") RecommendationSort sort,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
			@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false)
			String acceptLanguage,
			HttpServletRequest request
		) {
		MonthlyRecommendationService.MonthlyRecommendationPage page =
			service.getMonthlyRecommendations(
				year,
				month,
				serviceRegionCode,
				dateFilterType,
				customStartDate,
				customEndDate,
				travelStyles,
				sort,
				cursor,
				size,
				PlaceLanguage.fromAcceptLanguage(acceptLanguage));
		return ApiEnvelope.success(
			"MONTHLY_RECOMMENDATIONS_OK",
			MonthlyRecommendationDtos.from(page),
			TraceIdFilter.current(request));
	}
}
