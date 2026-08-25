package koready_backend.recommendation.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.recommendation.application.RecommendationExposureAdminService;

@Validated
@RestController
@RequestMapping("/api/v1/admin/recommendations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRecommendationController {

	private final RecommendationExposureAdminService service;

	public AdminRecommendationController(RecommendationExposureAdminService service) {
		this.service = service;
	}

	@DeleteMapping("/users/{userPublicId}/exposure-history")
	public ApiEnvelope<AdminRecommendationDtos.ExposureHistoryResetResponse> resetExposureHistory(
		@PathVariable @NotBlank @Size(max = 100) String userPublicId,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"RECOMMENDATION_EXPOSURE_HISTORY_RESET",
			AdminRecommendationDtos.from(service.reset(userPublicId)),
			TraceIdFilter.current(request));
	}
}
