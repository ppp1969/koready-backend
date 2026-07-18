package koready_backend.recommendation.application.port;

import java.time.Instant;

import koready_backend.recommendation.domain.RecommendationEventType;

public interface RecommendationEventRepository {

	boolean record(RecordEventCommand command);

	record RecordEventCommand(
		String eventPublicId,
		String userPublicId,
		String deckPublicId,
		long placeId,
		RecommendationEventType eventType,
		Instant occurredAt,
		Instant recordedAt
	) {
	}
}
