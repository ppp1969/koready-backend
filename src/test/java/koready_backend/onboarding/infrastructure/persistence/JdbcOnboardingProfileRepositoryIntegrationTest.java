package koready_backend.onboarding.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

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

import koready_backend.onboarding.application.OnboardingService;
import koready_backend.onboarding.application.OnboardingService.CompletionCommand;
import koready_backend.onboarding.application.exception.OnboardingCompletionException;
import koready_backend.place.domain.TravelStyle;
import koready_backend.user.domain.SignupStatus;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class JdbcOnboardingProfileRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OnboardingService service;

	@BeforeEach
	void migrationWasApplied() {
		Integer migrationCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '11'",
			Integer.class);
		assertEquals(1, migrationCount);
	}

	@Test
	void persistsACompleteProfileAndKeepsRetriesIdempotentAfterArchival() {
		long userId = user("usr_onboarding_db", SignupStatus.NEED_ONBOARDING);
		long locationId = location(userId);
		long firstPlaceId = place("onboarding-db-1");
		long secondPlaceId = place("onboarding-db-2");
		long candidateSetId = candidateSet("onb-db-v1", 101);
		candidateItem(candidateSetId, firstPlaceId, 1);
		candidateItem(candidateSetId, secondPlaceId, 2);
		CompletionCommand command = new CompletionCommand(
			locationId,
			List.of(TravelStyle.LOCAL_FOOD, TravelStyle.NATURE),
			"onb-db-v1",
			101,
			List.of(secondPlaceId, firstPlaceId));

		var first = service.complete("usr_onboarding_db", command);

		assertNotNull(first.completedAt());
		assertEquals(SignupStatus.COMPLETED, SignupStatus.valueOf(jdbcTemplate.queryForObject(
			"SELECT signup_status FROM users WHERE id = ?", String.class, userId)));
		assertEquals(locationId, jdbcTemplate.queryForObject(
			"SELECT default_location_id FROM users WHERE id = ?", Long.class, userId));
		assertEquals(
			List.of("LOCAL_FOOD", "NATURE"),
			jdbcTemplate.queryForList(
				"SELECT travel_style FROM user_travel_styles WHERE user_id = ? ORDER BY display_order",
				String.class,
				userId));
		assertEquals(
			List.of(secondPlaceId, firstPlaceId),
			jdbcTemplate.queryForList(
				"""
				SELECT place_id FROM user_onboarding_place_selections
				WHERE user_id = ? ORDER BY selected_order
				""",
				Long.class,
				userId));

		jdbcTemplate.update(
			"""
			UPDATE onboarding_candidate_sets
			SET status = 'ARCHIVED', archived_at = NOW(6)
			WHERE id = ?
			""",
			candidateSetId);
		jdbcTemplate.update(
			"UPDATE user_locations SET deleted_at = NOW(6) WHERE id = ?",
			locationId);

		var retry = service.complete("usr_onboarding_db", command);

		assertEquals(first.completedAt(), retry.completedAt());
		assertEquals(2, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_onboarding_place_selections WHERE user_id = ?",
			Integer.class,
			userId));
	}

	@Test
	void rollsBackBeforeWritingWhenASelectedPlaceIsOutsideThePublishedSet() {
		long userId = user("usr_onboarding_invalid", SignupStatus.NEED_ONBOARDING);
		long locationId = location(userId);
		long includedPlaceId = place("onboarding-db-included");
		long outsidePlaceId = place("onboarding-db-outside");
		long candidateSetId = candidateSet("onb-db-v2", 102);
		candidateItem(candidateSetId, includedPlaceId, 1);

		assertThrows(OnboardingCompletionException.class, () -> service.complete(
			"usr_onboarding_invalid",
			new CompletionCommand(
				locationId,
				List.of(TravelStyle.NATURE),
				"onb-db-v2",
				102,
				List.of(outsidePlaceId))));

		assertEquals("NEED_ONBOARDING", jdbcTemplate.queryForObject(
			"SELECT signup_status FROM users WHERE id = ?", String.class, userId));
		assertEquals(0, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_travel_styles WHERE user_id = ?",
			Integer.class,
			userId));
		assertEquals(0, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_onboarding_place_selections WHERE user_id = ?",
			Integer.class,
			userId));
	}

	private long user(String publicId, SignupStatus status) {
		jdbcTemplate.update(
			"INSERT INTO users (public_id, signup_status) VALUES (?, ?)",
			publicId,
			status.name());
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private long location(long userId) {
		jdbcTemplate.update(
			"""
			INSERT INTO user_locations (user_id, display_name, service_region_code)
			VALUES (?, '성신여자대학교', 'SEOUL')
			""",
			userId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM user_locations WHERE user_id = ? ORDER BY id DESC LIMIT 1",
			Long.class,
			userId);
	}

	private long place(String contentId) {
		jdbcTemplate.update(
			"""
			INSERT INTO places (kto_content_id, service_region_code, show_flag, active)
			VALUES (?, 'SEOUL', TRUE, TRUE)
			""",
			contentId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?", Long.class, contentId);
	}

	private long candidateSet(String publicId, int version) {
		jdbcTemplate.update(
			"""
			INSERT INTO onboarding_candidate_sets
			    (public_id, title, version, status, published_by_subject, published_at)
			VALUES (?, 'Integration set', ?, 'PUBLISHED', 'test-operator', NOW(6))
			""",
			publicId,
			version);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM onboarding_candidate_sets WHERE public_id = ?",
			Long.class,
			publicId);
	}

	private void candidateItem(long candidateSetId, long placeId, int displayOrder) {
		jdbcTemplate.update(
			"""
			INSERT INTO onboarding_candidate_set_items
			    (candidate_set_id, place_id, display_order,
			     curator_message_ko, display_tags_json)
			VALUES (?, ?, ?, '추천 장소', JSON_ARRAY())
			""",
			candidateSetId,
			placeId,
			displayOrder);
	}
}
