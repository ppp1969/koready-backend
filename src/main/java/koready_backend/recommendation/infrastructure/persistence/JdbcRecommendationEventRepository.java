package koready_backend.recommendation.infrastructure.persistence;

import java.sql.Timestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.recommendation.application.port.RecommendationEventRepository;

@Repository
public class JdbcRecommendationEventRepository implements RecommendationEventRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcRecommendationEventRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean record(RecordEventCommand command) {
		int inserted = jdbcTemplate.update(
			"""
			INSERT INTO user_place_events
			    (public_id, user_id, place_id, event_type, deck_id,
			     policy_version, suppression_days, occurred_at, created_at)
			SELECT ?, owner.id, item.place_id, ?, deck.id,
			       NULL, NULL, ?, ?
			FROM recommendation_decks deck
			JOIN users owner ON owner.id = deck.user_id
			JOIN recommendation_deck_items item
			  ON item.deck_id = deck.id AND item.place_id = ?
			WHERE deck.public_id = ?
			  AND owner.public_id = ?
			  AND owner.deleted_at IS NULL
			  AND item.served_at IS NOT NULL
			""",
			command.eventPublicId(),
			command.eventType().name(),
			Timestamp.from(command.occurredAt()),
			Timestamp.from(command.recordedAt()),
			command.placeId(),
			command.deckPublicId(),
			command.userPublicId());
		return inserted == 1;
	}
}
