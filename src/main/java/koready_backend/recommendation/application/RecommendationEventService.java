package koready_backend.recommendation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.recommendation.application.exception.RecommendationDeckNotFoundException;
import koready_backend.recommendation.application.port.RecommendationEventRepository;
import koready_backend.recommendation.application.port.RecommendationEventRepository.RecordEventCommand;
import koready_backend.recommendation.domain.RecommendationEventType;

@Service
public class RecommendationEventService {

	private final RecommendationEventRepository repository;
	private final Clock clock;

	@Autowired
	public RecommendationEventService(RecommendationEventRepository repository) {
		this(repository, Clock.systemUTC());
	}

	RecommendationEventService(
		RecommendationEventRepository repository,
		Clock clock
	) {
		this.repository = repository;
		this.clock = clock;
	}

	public RecommendationEvent recordEvent(
		String userPublicId,
		String deckPublicId,
		long placeId,
		RecommendationEventType eventType,
		Instant occurredAt
	) {
		Instant recordedAt = clock.instant();
		Instant effectiveOccurredAt = occurredAt == null ? recordedAt : occurredAt;
		String eventPublicId = "recevt_" + UUID.randomUUID();
		boolean recorded = repository.record(new RecordEventCommand(
			eventPublicId,
			userPublicId,
			deckPublicId,
			placeId,
			eventType,
			effectiveOccurredAt,
			recordedAt));
		if (!recorded) {
			throw new RecommendationDeckNotFoundException();
		}
		return new RecommendationEvent(
			eventPublicId,
			deckPublicId,
			placeId,
			eventType,
			recordedAt);
	}

	public record RecommendationEvent(
		String eventId,
		String deckId,
		long placeId,
		RecommendationEventType eventType,
		Instant recordedAt
	) {
	}
}
