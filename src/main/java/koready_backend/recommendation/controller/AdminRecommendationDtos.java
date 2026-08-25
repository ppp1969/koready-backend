package koready_backend.recommendation.controller;

import koready_backend.recommendation.application.RecommendationExposureAdminService;

final class AdminRecommendationDtos {

	private AdminRecommendationDtos() {
	}

	static ExposureHistoryResetResponse from(
		RecommendationExposureAdminService.ResetView view
	) {
		return new ExposureHistoryResetResponse(
			view.userPublicId(),
			view.deletedSuppressionStateCount(),
			view.deletedCardServedEventCount());
	}

	record ExposureHistoryResetResponse(
		String userPublicId,
		int deletedSuppressionStateCount,
		int deletedCardServedEventCount
	) {
	}
}
