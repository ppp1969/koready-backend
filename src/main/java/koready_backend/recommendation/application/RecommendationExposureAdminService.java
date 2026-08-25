package koready_backend.recommendation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.recommendation.application.exception.RecommendationUserNotFoundException;
import koready_backend.recommendation.application.port.RecommendationDeckRepository;

@Service
public class RecommendationExposureAdminService {

	private final RecommendationDeckRepository repository;

	public RecommendationExposureAdminService(RecommendationDeckRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public ResetView reset(String userPublicId) {
		if (userPublicId == null || userPublicId.isBlank()) {
			throw new IllegalArgumentException("userPublicId is required");
		}
		var result = repository.resetExposureHistory(userPublicId.strip())
			.orElseThrow(RecommendationUserNotFoundException::new);
		return new ResetView(
			result.userPublicId(),
			result.deletedSuppressionStateCount(),
			result.deletedCardServedEventCount());
	}

	public record ResetView(
		String userPublicId,
		int deletedSuppressionStateCount,
		int deletedCardServedEventCount
	) {
	}
}
