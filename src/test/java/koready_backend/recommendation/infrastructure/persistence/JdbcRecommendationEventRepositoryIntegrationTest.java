package koready_backend.recommendation.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.recommendation.application.port.RecommendationEventRepository;
import koready_backend.recommendation.application.port.RecommendationEventRepository.RecordEventCommand;
import koready_backend.recommendation.domain.RecommendationEventType;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class JdbcRecommendationEventRepositoryIntegrationTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-07-19T04:00:00.123456Z");
	private static final Instant RECORDED_AT = Instant.parse("2026-07-19T04:00:01.654321Z");
	private static final String OWNER = "usr_event_repository_owner";
	private static final String DECK = "rec_event_repository";

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	RecommendationEventRepository repository;

	private long placeId;

	@BeforeEach
	void setUp() {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8'",
			Integer.class);
		assertEquals(1, count);

		UserFixture owner = user(OWNER);
		placeId = place("event-repository-place");
		deck(owner, DECK, placeId);
	}

	@Test
	void recordsOnlyAfterTheOwnedCardWasServedAndKeepsBothTimestamps() {
		RecordEventCommand command = command(
			"recevt_repository",
			OWNER,
			DECK,
			placeId,
			RecommendationEventType.CARD_EXPANDED);

		assertFalse(repository.record(command));
		assertEquals(0, eventCount("recevt_repository"));

		jdbcTemplate.update(
			"UPDATE recommendation_deck_items SET served_at = ? WHERE place_id = ?",
			Timestamp.from(OCCURRED_AT.minusSeconds(1)),
			placeId);

		assertTrue(repository.record(command));
		EventRow row = jdbcTemplate.queryForObject(
			"""
			SELECT public_id, event_type, occurred_at, created_at,
			       policy_version, suppression_days
			FROM user_place_events
			WHERE public_id = ?
			""",
			(rs, rowNumber) -> new EventRow(
				rs.getString("public_id"),
				rs.getString("event_type"),
				rs.getTimestamp("occurred_at").toInstant(),
				rs.getTimestamp("created_at").toInstant(),
				rs.getString("policy_version"),
				(Integer)rs.getObject("suppression_days")),
			"recevt_repository");
		assertEquals("recevt_repository", row.publicId());
		assertEquals("CARD_EXPANDED", row.eventType());
		assertEquals(OCCURRED_AT, row.occurredAt());
		assertEquals(RECORDED_AT, row.recordedAt());
		assertNull(row.policyVersion());
		assertNull(row.suppressionDays());
	}

	@Test
	void rejectsForeignMissingAndUnservedCardsWithoutCreatingRows() {
		user("usr_event_repository_other");
		jdbcTemplate.update(
			"UPDATE recommendation_deck_items SET served_at = ? WHERE place_id = ?",
			Timestamp.from(OCCURRED_AT.minusSeconds(1)),
			placeId);

		assertFalse(repository.record(command(
			"recevt_foreign",
			"usr_event_repository_other",
			DECK,
			placeId,
			RecommendationEventType.CARD_NEXT)));
		assertFalse(repository.record(command(
			"recevt_missing",
			OWNER,
			"rec_missing",
			placeId,
			RecommendationEventType.CARD_NEXT)));
		assertFalse(repository.record(command(
			"recevt_wrong_place",
			OWNER,
			DECK,
			place("event-repository-other-place"),
			RecommendationEventType.CARD_NEXT)));
		assertEquals(0, eventCount("recevt_foreign"));
		assertEquals(0, eventCount("recevt_missing"));
		assertEquals(0, eventCount("recevt_wrong_place"));
	}

	private RecordEventCommand command(
		String eventPublicId,
		String userPublicId,
		String deckPublicId,
		long targetPlaceId,
		RecommendationEventType eventType
	) {
		return new RecordEventCommand(
			eventPublicId,
			userPublicId,
			deckPublicId,
			targetPlaceId,
			eventType,
			OCCURRED_AT,
			RECORDED_AT);
	}

	private UserFixture user(String publicId) {
		jdbcTemplate.update(
			"INSERT INTO users (public_id, signup_status) VALUES (?, 'COMPLETED')",
			publicId);
		long userId = jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
		jdbcTemplate.update(
			"""
			INSERT INTO user_locations (user_id, display_name, service_region_code)
			VALUES (?, ?, 'SEOUL')
			""",
			userId,
			publicId + " location");
		long locationId = jdbcTemplate.queryForObject(
			"SELECT id FROM user_locations WHERE user_id = ?", Long.class, userId);
		jdbcTemplate.update(
			"UPDATE users SET default_location_id = ? WHERE id = ?",
			locationId,
			userId);
		return new UserFixture(userId, locationId);
	}

	private long place(String ktoContentId) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, service_region_code, show_flag, active, data_quality_score)
			VALUES (?, 'SEOUL', TRUE, TRUE, 90.00)
			""",
			ktoContentId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			ktoContentId);
	}

	private void deck(
		UserFixture owner,
		String deckPublicId,
		long targetPlaceId
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO recommendation_decks
			    (public_id, user_id, scope, origin_location_id,
			     origin_display_name, origin_service_region_code, language,
			     seed, cursor_version, suppression_policy_version,
			     suppression_days, page_size, created_at, expires_at)
			VALUES (?, ?, 'NEARBY', ?, 'Owner location', 'SEOUL', 'EN',
			        ?, 1, 'recommendation-suppression-v1', 30, 20, ?, ?)
			""",
			deckPublicId,
			owner.userId(),
			owner.locationId(),
			"a".repeat(64),
			Timestamp.from(OCCURRED_AT.minusSeconds(60)),
			Timestamp.from(OCCURRED_AT.plusSeconds(3600)));
		long deckId = jdbcTemplate.queryForObject(
			"SELECT id FROM recommendation_decks WHERE public_id = ?",
			Long.class,
			deckPublicId);
		jdbcTemplate.update(
			"""
			INSERT INTO recommendation_deck_items
			    (deck_id, place_id, display_order, title, location_text,
			     service_region_code, tags_json, match_rank,
			     travel_style_matched, preference_tag_matched, matched_tag_codes_json)
			VALUES (?, ?, 1, 'Event place', 'Seoul', 'SEOUL', JSON_ARRAY(),
			        3, FALSE, FALSE, JSON_ARRAY())
			""",
			deckId,
			targetPlaceId);
	}

	private int eventCount(String eventPublicId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_place_events WHERE public_id = ?",
			Integer.class,
			eventPublicId);
	}

	private record UserFixture(long userId, long locationId) {
	}

	private record EventRow(
		String publicId,
		String eventType,
		Instant occurredAt,
		Instant recordedAt,
		String policyVersion,
		Integer suppressionDays
	) {
	}
}
