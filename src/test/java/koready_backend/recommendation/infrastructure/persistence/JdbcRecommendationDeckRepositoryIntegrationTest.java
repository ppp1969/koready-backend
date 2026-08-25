package koready_backend.recommendation.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.RecommendationDeckService;
import koready_backend.recommendation.application.port.RecommendationDeckRepository;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.CardSnapshot;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.CreateDeckPlan;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.PagePlan;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.StoredDeckPage;
import koready_backend.recommendation.domain.RecommendationScope;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcRecommendationDeckRepositoryIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-07-19T00:00:00Z");
	private static final String USER = "usr_recommendation_owner";

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	RecommendationDeckRepository repository;

	@BeforeEach
	void recommendationMigrationIsApplied() {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8'",
			Integer.class);
		assertEquals(1, count);
	}

	@Test
	void servesEachPageOnceAndDoesNotExtendSuppressionOnReplay() {
		UserFixture user = user(USER, "SEOUL", TravelStyle.NATURE);
		long first = place("deck-first", "SEOUL", TravelStyle.NATURE, "90.00");
		long second = place("deck-second", "SEOUL", TravelStyle.LOCAL_FOOD, "80.00");
		long third = place("deck-third", "GANGWON", TravelStyle.NATURE, "70.00");
		CreateDeckPlan plan = plan(user, List.of(
			card(first, "First", ServiceRegionCode.SEOUL, TravelStyle.NATURE, 2),
			card(second, "Second", ServiceRegionCode.SEOUL, TravelStyle.LOCAL_FOOD, 3),
			card(third, "Third", ServiceRegionCode.GANGWON, TravelStyle.NATURE, 2)));

		StoredDeckPage created = repository.createDeck(plan);

		assertEquals(List.of(first, second),
			created.cards().stream().map(CardSnapshot::placeId).toList());
		assertEquals("rec_integration_test-cursor-page-2", created.nextCursor());
		assertTrue(created.hasMore());
		assertEquals(2, eventCount(plan.deckPublicId()));
		assertEquals(2, stateCount(user.userId()));
		Instant firstSuppression = suppressUntil(user.userId(), first);
		assertEquals(NOW.plusSeconds(14L * 24 * 60 * 60), firstSuppression);

		StoredDeckPage replayed = repository.findPage(USER, plan.deckPublicId(), null, NOW.plusSeconds(60))
			.orElseThrow();
		assertEquals(List.of(first, second),
			replayed.cards().stream().map(CardSnapshot::placeId).toList());
		assertEquals(2, eventCount(plan.deckPublicId()));
		assertEquals(firstSuppression, suppressUntil(user.userId(), first));
		assertEquals(1, servedCount(user.userId(), first));

		StoredDeckPage next = repository.findPage(
			USER, plan.deckPublicId(), "rec_integration_test-cursor-page-2", NOW.plusSeconds(120))
			.orElseThrow();
		assertEquals(List.of(third), next.cards().stream().map(CardSnapshot::placeId).toList());
		assertFalse(next.hasMore());
		assertEquals(3, eventCount(plan.deckPublicId()));
		assertEquals(3, stateCount(user.userId()));

		assertEquals(2, repository.findEligibleCandidates(
			user.userId(),
			NOW.plusSeconds(180),
			PlaceLanguage.EN,
			RecommendationScope.NEARBY,
			ServiceRegionCode.SEOUL,
			100).size());
		assertEquals(2, repository.findEligibleCandidates(
			user.userId(),
			NOW.plusSeconds(14L * 24 * 60 * 60 + 120),
			PlaceLanguage.EN,
			RecommendationScope.NEARBY,
			ServiceRegionCode.SEOUL,
			100).size());
	}

	@Test
	void keepsCardSnapshotsStableAndHidesDecksFromOtherUsers() {
		UserFixture owner = user(USER, "SEOUL", TravelStyle.NATURE);
		user("usr_other", "SEOUL", TravelStyle.NATURE);
		long placeId = place("snapshot-place", "SEOUL", TravelStyle.NATURE, "90.00");
		CreateDeckPlan plan = plan(owner, List.of(
			card(placeId, "Original title", ServiceRegionCode.SEOUL, TravelStyle.NATURE, 2)));
		repository.createDeck(plan);

		jdbcTemplate.update(
			"UPDATE place_localizations SET title = 'Changed title' WHERE place_id = ? AND language = 'EN'",
			placeId);

		StoredDeckPage replayed = repository.findPage(USER, plan.deckPublicId(), null, NOW.plusSeconds(10))
			.orElseThrow();
		assertEquals("Original title", replayed.cards().getFirst().title());
		assertTrue(repository.findPage(
			"usr_other", plan.deckPublicId(), null, NOW.plusSeconds(10)).isEmpty());
		assertTrue(repository.findPage(
			USER, plan.deckPublicId(), "unknown-cursor", NOW.plusSeconds(10)).isEmpty());
	}

	@Test
	void resetsOnlyTheRequestedUsersSuppressionAndServedEvents() {
		UserFixture target = user(USER, "SEOUL", TravelStyle.NATURE);
		UserFixture other = user("usr_reset_other", "SEOUL", TravelStyle.NATURE);
		long targetPlace = place("reset-target", "SEOUL", TravelStyle.NATURE, "90.00");
		long otherPlace = place("reset-other", "SEOUL", TravelStyle.NATURE, "80.00");
		repository.createDeck(plan(target, "rec_reset_target", List.of(
			card(targetPlace, "Target", ServiceRegionCode.SEOUL, TravelStyle.NATURE, 2)), 1));
		repository.createDeck(plan(other, "rec_reset_other", List.of(
			card(otherPlace, "Other", ServiceRegionCode.SEOUL, TravelStyle.NATURE, 2)), 1));
		jdbcTemplate.update("""
			INSERT INTO user_place_events
			    (public_id, user_id, place_id, event_type, deck_id, occurred_at)
			SELECT 'recevt_expanded_reset', ?, ?, 'CARD_EXPANDED', id, ?
			FROM recommendation_decks WHERE public_id = 'rec_reset_target'
			""", target.userId(), targetPlace, Timestamp.from(NOW.plusSeconds(1)));

		var result = repository.resetExposureHistory(USER).orElseThrow();

		assertEquals(1, result.deletedSuppressionStateCount());
		assertEquals(1, result.deletedCardServedEventCount());
		assertEquals(0, stateCount(target.userId()));
		assertEquals(1, stateCount(other.userId()));
		assertEquals(0, eventTypeCount(target.userId(), "CARD_SERVED"));
		assertEquals(1, eventTypeCount(target.userId(), "CARD_EXPANDED"));
		assertEquals(1, eventTypeCount(other.userId(), "CARD_SERVED"));
	}

	@Test
	void skipsCardsThatAnotherDeckServedBeforeThePageWasOpened() {
		UserFixture user = user(USER, "SEOUL", TravelStyle.NATURE);
		long first = place("delayed-first", "SEOUL", TravelStyle.NATURE, "90.00");
		long overlapping = place("delayed-overlap", "SEOUL", TravelStyle.LOCAL_FOOD, "80.00");
		CreateDeckPlan delayedDeck = plan(
			user,
			"rec_delayed_deck",
			List.of(
				card(first, "First", ServiceRegionCode.SEOUL, TravelStyle.NATURE, 2),
				card(overlapping, "Overlap", ServiceRegionCode.SEOUL, TravelStyle.LOCAL_FOOD, 3)),
			1);
		CreateDeckPlan competingDeck = plan(
			user,
			"rec_competing_deck",
			List.of(card(
				overlapping,
				"Overlap",
				ServiceRegionCode.SEOUL,
				TravelStyle.LOCAL_FOOD,
				3)),
			1);

		repository.createDeck(delayedDeck);
		repository.createDeck(competingDeck);
		Instant originalSuppression = suppressUntil(user.userId(), overlapping);

		StoredDeckPage delayedPage = repository.findPage(
			USER, delayedDeck.deckPublicId(), "rec_delayed_deck-cursor-page-2", NOW.plusSeconds(60))
			.orElseThrow();
		StoredDeckPage replayed = repository.findPage(
			USER, delayedDeck.deckPublicId(), "rec_delayed_deck-cursor-page-2", NOW.plusSeconds(120))
			.orElseThrow();

		assertEquals(List.of(overlapping),
			delayedPage.cards().stream().map(CardSnapshot::placeId).toList());
		assertEquals(List.of(overlapping),
			replayed.cards().stream().map(CardSnapshot::placeId).toList());
		assertEquals(2, eventCount(delayedDeck.deckPublicId()));
		assertEquals(1, eventCount(competingDeck.deckPublicId()));
		assertEquals(2, servedCount(user.userId(), overlapping));
		assertTrue(suppressUntil(user.userId(), overlapping).isAfter(originalSuppression));
	}

	@Test
	void resolvesOnlyAnOwnedActiveOriginLocation() {
		UserFixture owner = user(USER, "SEOUL", TravelStyle.NATURE);
		UserFixture other = user("usr_location_other", "GANGWON", TravelStyle.LOCAL_FOOD);

		assertEquals(owner.locationId(), repository.findUserContext(USER, null)
			.orElseThrow().locationId());
		assertEquals(owner.locationId(), repository.findUserContext(USER, owner.locationId())
			.orElseThrow().locationId());
		assertTrue(repository.findUserContext(USER, other.locationId()).isEmpty());

		jdbcTemplate.update("UPDATE user_locations SET deleted_at = ? WHERE id = ?",
			Timestamp.from(NOW), owner.locationId());
		assertTrue(repository.findUserContext(USER, null).isEmpty());
	}

	@Test
	void ordersCandidatesByActiveHeartCountBeforeQuality() {
		UserFixture target = user(USER, "SEOUL", TravelStyle.NATURE);
		long popular = place("popular-place", "SEOUL", TravelStyle.NATURE, "50.00");
		long highQuality = place("high-quality-place", "SEOUL", TravelStyle.NATURE, "100.00");
		UserFixture fan1 = user("usr_deck_fan_1", "SEOUL", TravelStyle.LOCAL_FOOD);
		UserFixture fan2 = user("usr_deck_fan_2", "SEOUL", TravelStyle.LOCAL_FOOD);
		save(fan1.userId(), popular, false);
		save(fan2.userId(), popular, false);
		save(fan1.userId(), highQuality, true);

		List<Long> candidates = repository.findEligibleCandidates(
			target.userId(), NOW, PlaceLanguage.EN, RecommendationScope.NATIONWIDE,
			ServiceRegionCode.SEOUL, 10).stream()
			.map(candidate -> candidate.placeId())
			.toList();

		assertEquals(List.of(popular, highQuality), candidates);
	}

	@Test
	void ordersLimitedCandidatesByScopeStyleMatchAndQuality() {
		UserFixture user = user(USER, "SEOUL", TravelStyle.NATURE);
		long nearbyMatch = place("nearby-match", "SEOUL", TravelStyle.NATURE, "50.00");
		long nearbyExplore = place("nearby-explore", "SEOUL", TravelStyle.LOCAL_FOOD, "100.00");
		long nationwideMatch = place("nationwide-match", "GANGWON", TravelStyle.NATURE, "90.00");

		List<Long> nearby = repository.findEligibleCandidates(
			user.userId(),
			NOW,
			PlaceLanguage.EN,
			RecommendationScope.NEARBY,
			ServiceRegionCode.SEOUL,
			10).stream().map(candidate -> candidate.placeId()).toList();
		List<Long> nationwide = repository.findEligibleCandidates(
			user.userId(),
			NOW,
			PlaceLanguage.EN,
			RecommendationScope.NATIONWIDE,
			ServiceRegionCode.SEOUL,
			2).stream().map(candidate -> candidate.placeId()).toList();

		assertEquals(List.of(nearbyMatch, nearbyExplore), nearby);
		assertEquals(List.of(nationwideMatch, nearbyMatch), nationwide);
	}

	@Test
	void placesEndedFestivalsAfterCurrentCandidates() {
		UserFixture user = user(USER, "SEOUL", TravelStyle.LOCAL_FESTIVAL);
		long ended = place("ended-festival", "SEOUL", TravelStyle.LOCAL_FESTIVAL, "100.00");
		occurrence(ended, NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(5)));
		long current = place("current-festival", "SEOUL", TravelStyle.LOCAL_FESTIVAL, "50.00");
		occurrence(current, NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(1)));

		List<Long> candidates = repository.findEligibleCandidates(
			user.userId(), NOW, PlaceLanguage.EN, RecommendationScope.NEARBY,
			ServiceRegionCode.SEOUL, 10).stream()
			.map(candidate -> candidate.placeId())
			.toList();

		assertEquals(List.of(current, ended), candidates);
	}

	@Test
	void excludesAiTranslatedPlacesFromRecommendationCandidates() {
		UserFixture user = user(USER, "SEOUL", TravelStyle.NATURE);
		long trusted = place("trusted-english", "SEOUL", TravelStyle.NATURE, "80.00");
		long aiTranslated = place("ai-translated", "SEOUL", TravelStyle.NATURE, "100.00");
		jdbcTemplate.update(
			"""
			UPDATE place_localizations
			SET translation_source = 'AI_TRANSLATED'
			WHERE place_id = ? AND language = 'EN'
			""",
			aiTranslated);

		List<Long> candidates = repository.findEligibleCandidates(
			user.userId(),
			NOW,
			PlaceLanguage.EN,
			RecommendationScope.NEARBY,
			ServiceRegionCode.SEOUL,
			10).stream().map(candidate -> candidate.placeId()).toList();

		assertEquals(List.of(trusted), candidates);
	}

	private UserFixture user(String publicId, String region, TravelStyle style) {
		jdbcTemplate.update(
			"""
			INSERT INTO users
			    (public_id, preferred_language, signup_status)
			VALUES (?, 'EN', 'COMPLETED')
			""",
			publicId);
		long userId = jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
		jdbcTemplate.update(
			"""
			INSERT INTO user_locations
			    (user_id, display_name, service_region_code)
			VALUES (?, ?, ?)
			""",
			userId, publicId + " campus", region);
		long locationId = jdbcTemplate.queryForObject(
			"SELECT id FROM user_locations WHERE user_id = ?", Long.class, userId);
		jdbcTemplate.update("UPDATE users SET default_location_id = ? WHERE id = ?",
			locationId, userId);
		jdbcTemplate.update(
			"""
			INSERT INTO user_travel_styles (user_id, travel_style, display_order)
			VALUES (?, ?, 1)
			""",
			userId, style.name());
		return new UserFixture(userId, publicId, locationId, ServiceRegionCode.valueOf(region));
	}

	private long place(
		String sourceId,
		String region,
		TravelStyle style,
		String quality
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, service_region_code, show_flag, active,
			     data_quality_score, first_image_url)
			VALUES (?, ?, TRUE, TRUE, ?, ?)
			""",
			sourceId, region, new BigDecimal(quality),
			"https://example.invalid/" + sourceId + ".jpg");
		long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?", Long.class, sourceId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, overview, address_text, translation_source)
			VALUES (?, 'KO', ?, 'Korean overview', 'Korean address', 'MANUAL_EDITED'),
			       (?, 'EN', ?, 'English overview', 'English address', 'MANUAL_EDITED')
			""",
			placeId, "Korean " + sourceId, placeId, "English " + sourceId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_style_mappings (place_id, travel_style, source, confidence)
			VALUES (?, ?, 'MANUAL', 1.0000)
			""",
			placeId, style.name());
		return placeId;
	}

	private void save(long userId, long placeId, boolean deleted) {
		jdbcTemplate.update(
			"""
			INSERT INTO user_saved_places
			    (user_id, place_id, source, saved_at, updated_at, deleted_at)
			VALUES (?, ?, 'RECOMMENDATION_CARD', NOW(6), NOW(6), ?)
			""",
			userId, placeId, deleted ? Timestamp.from(NOW) : null);
	}

	private CreateDeckPlan plan(UserFixture user, List<CardSnapshot> cards) {
		return plan(user, "rec_integration_test", cards, 2);
	}

	private void occurrence(long placeId, Instant start, Instant end) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_event_occurrences
			    (place_id, event_year, occurrence_sequence, start_date, end_date,
			     provider, source_content_id, source_operation, visible_from,
			     date_validation_status)
			VALUES (?, 2026, 1, DATE(?), DATE(?), 'MANUAL', ?,
			        'INTEGRATION_TEST', DATE(?), 'VALID')
			""",
			placeId,
			Timestamp.from(start),
			Timestamp.from(end),
			"event-" + placeId,
			Timestamp.from(start.minus(Duration.ofDays(180))));
	}

	private CreateDeckPlan plan(
		UserFixture user,
		String deckPublicId,
		List<CardSnapshot> cards,
		int pageSize
	) {
		List<PagePlan> pages = new ArrayList<>();
		int pageCount = Math.max(1, (cards.size() + pageSize - 1) / pageSize);
		for (int index = 0; index < pageCount; index++) {
			pages.add(new PagePlan(
				index + 1,
				deckPublicId + "-cursor-page-" + (index + 1),
				index * pageSize + 1,
				Math.min(cards.size(), (index + 1) * pageSize)));
		}
		return new CreateDeckPlan(
			deckPublicId,
			user.userId(),
			user.publicId(),
			RecommendationScope.NEARBY,
			user.locationId(),
			user.publicId() + " campus",
			user.region(),
			PlaceLanguage.EN,
			"seed",
			1,
			RecommendationDeckService.SUPPRESSION_POLICY_VERSION,
			RecommendationDeckService.SUPPRESSION_DAYS,
			pageSize,
			NOW,
			NOW.plusSeconds(24 * 60 * 60),
			cards,
			List.copyOf(pages));
	}

	private CardSnapshot card(
		long placeId,
		String title,
		ServiceRegionCode region,
		TravelStyle style,
		int rank
	) {
		return new CardSnapshot(
			placeId,
			title,
			region.name(),
			null,
			"Snapshot description",
			region,
			style,
			List.of(style.name()),
			rank,
			rank == 2,
			false,
			List.of());
	}

	private int eventCount(String deckPublicId) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*) FROM user_place_events upe
			JOIN recommendation_decks deck ON deck.id = upe.deck_id
			WHERE deck.public_id = ?
			""",
			Integer.class,
			deckPublicId);
	}

	private int stateCount(long userId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_place_recommendation_states WHERE user_id = ?",
			Integer.class,
			userId);
	}

	private int eventTypeCount(long userId, String eventType) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_place_events WHERE user_id = ? AND event_type = ?",
			Integer.class,
			userId,
			eventType);
	}

	private int servedCount(long userId, long placeId) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT served_count FROM user_place_recommendation_states
			WHERE user_id = ? AND place_id = ?
			""",
			Integer.class,
			userId,
			placeId);
	}

	private Instant suppressUntil(long userId, long placeId) {
		Timestamp value = jdbcTemplate.queryForObject(
			"""
			SELECT suppress_until FROM user_place_recommendation_states
			WHERE user_id = ? AND place_id = ?
			""",
			Timestamp.class,
			userId,
			placeId);
		return value.toInstant();
	}

	private record UserFixture(
		long userId,
		String publicId,
		long locationId,
		ServiceRegionCode region
	) {
	}
}
