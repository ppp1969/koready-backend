package koready_backend.location.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

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

import koready_backend.location.application.port.UserLocationRepository;
import koready_backend.location.application.port.UserLocationRepository.NewLocation;
import koready_backend.location.application.port.UserLocationRepository.UserLocationRecord;
import koready_backend.place.domain.ServiceRegionCode;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcUserLocationRepositoryIntegrationTest {

	private static final Instant FIRST_CREATED_AT =
		Instant.parse("2026-07-18T07:00:00Z");
	private static final Instant SECOND_CREATED_AT =
		Instant.parse("2026-07-19T07:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserLocationRepository repository;

	@Test
	void createsAndReadsACompleteLocationWithoutExposingLegacyRows() {
		long userId = user("usr_location_db");
		legacyLocation(userId, "기존 위치");

		UserLocationRecord created = repository.create(
			userId, newLocation("학교", "kakao-100"), SECOND_CREATED_AT);
		repository.updateDefaultLocation(userId, created.locationId(), SECOND_CREATED_AT);

		List<UserLocationRecord> locations = repository.findAllCompleteActive(
			userId, created.locationId());
		assertEquals(1, locations.size());
		assertEquals(created, locations.getFirst());
		assertEquals(37.5666, created.latitude());
		assertEquals(126.9784, created.longitude());
		assertEquals(ServiceRegionCode.SEOUL, created.serviceRegionCode());
		assertEquals(created.locationId(), repository
			.findActiveUser("usr_location_db").orElseThrow().defaultLocationId());
	}

	@Test
	void ordersTheDefaultFirstThenTheRemainingLocationsNewestFirst() {
		long userId = user("usr_location_order");
		UserLocationRecord first = repository.create(
			userId, newLocation("학교", "kakao-101"), FIRST_CREATED_AT);
		UserLocationRecord second = repository.create(
			userId, newLocation("집", "kakao-102"), SECOND_CREATED_AT);

		List<UserLocationRecord> rows = repository.findAllCompleteActive(
			userId, first.locationId());

		assertEquals(List.of(first.locationId(), second.locationId()), rows.stream()
			.map(UserLocationRecord::locationId)
			.toList());
	}

	@Test
	void locksActiveUsersAndExcludesDeletedUsers() {
		long activeUserId = user("usr_location_active");
		long deletedUserId = user("usr_location_deleted");
		jdbcTemplate.update("UPDATE users SET deleted_at = NOW(6) WHERE id = ?",
			deletedUserId);

		var activeUser = repository
			.findActiveUserForUpdate("usr_location_active")
			.orElseThrow();
		assertEquals(activeUserId, activeUser.userId());
		assertNull(activeUser.defaultLocationId());
		assertTrue(repository.findActiveUser("usr_location_deleted").isEmpty());
		assertTrue(repository.findActiveUserForUpdate("usr_location_missing").isEmpty());
	}

	@Test
	void enforcesOwnershipAndSoftDeletionForLocationLookups() {
		long ownerId = user("usr_location_owner");
		long otherId = user("usr_location_other");
		UserLocationRecord location = repository.create(
			ownerId, newLocation(null, "kakao-200"), SECOND_CREATED_AT);

		assertTrue(repository.findCompleteActive(ownerId, location.locationId()).isPresent());
		assertTrue(repository.findCompleteActive(otherId, location.locationId()).isEmpty());

		repository.softDelete(ownerId, location.locationId(), SECOND_CREATED_AT.plusSeconds(30));

		assertTrue(repository.findCompleteActive(ownerId, location.locationId()).isEmpty());
		assertEquals(1, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_locations WHERE id = ? AND deleted_at IS NOT NULL",
			Integer.class,
			location.locationId()));
	}

	@Test
	void findsTheNewestRemainingCompleteLocationForDefaultReplacement() {
		long userId = user("usr_location_replacement");
		UserLocationRecord first = repository.create(
			userId, newLocation("학교", "kakao-301"), FIRST_CREATED_AT);
		UserLocationRecord second = repository.create(
			userId, newLocation("집", "kakao-302"), SECOND_CREATED_AT);
		UserLocationRecord excluded = repository.create(
			userId, newLocation("기숙사", "kakao-303"), SECOND_CREATED_AT.plusSeconds(60));

		UserLocationRecord replacement = repository
			.findNewestCompleteActiveExcluding(userId, excluded.locationId())
			.orElseThrow();

		assertEquals(second.locationId(), replacement.locationId());
		assertFalse(first.locationId() == replacement.locationId());
	}

	@Test
	void clearsTheUsersDefaultLocation() {
		long userId = user("usr_location_clear_default");
		UserLocationRecord location = repository.create(
			userId, newLocation(null, "kakao-401"), SECOND_CREATED_AT);
		repository.updateDefaultLocation(userId, location.locationId(), SECOND_CREATED_AT);

		repository.updateDefaultLocation(userId, null, SECOND_CREATED_AT.plusSeconds(1));

		assertNull(jdbcTemplate.queryForObject(
			"SELECT default_location_id FROM users WHERE id = ?",
			Long.class,
			userId));
	}

	private long user(String publicId) {
		jdbcTemplate.update(
			"INSERT INTO users (public_id, signup_status) VALUES (?, 'NEED_ONBOARDING')",
			publicId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private void legacyLocation(long userId, String displayName) {
		jdbcTemplate.update(
			"""
			INSERT INTO user_locations (user_id, display_name, service_region_code)
			VALUES (?, ?, 'SEOUL')
			""",
			userId,
			displayName);
	}

	private static NewLocation newLocation(String customLabel, String providerPlaceId) {
		return new NewLocation(
			"서울시청",
			customLabel,
			"KAKAO",
			providerPlaceId,
			"서울특별시 중구 세종대로 110",
			"서울특별시 중구 태평로1가 31",
			37.5666,
			126.9784,
			"서울특별시",
			"중구",
			"태평로1가",
			ServiceRegionCode.SEOUL);
	}
}
