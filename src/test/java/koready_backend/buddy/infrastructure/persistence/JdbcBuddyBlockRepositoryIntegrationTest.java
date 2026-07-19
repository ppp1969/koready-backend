package koready_backend.buddy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.buddy.application.port.BuddyBlockRepository;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcBuddyBlockRepositoryIntegrationTest {

	private static final Instant FIRST = Instant.parse("2026-07-19T04:00:00Z");
	private static final Instant SECOND = Instant.parse("2026-07-19T05:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BuddyBlockRepository repository;

	@Test
	void storesOneDirectionalBlockAndPreservesTheFirstTimestamp() {
		long blocker = user("usr_blocker_db");
		long blocked = user("usr_blocked_db");
		long profileId = profile(blocked, "Blocked Buddy");

		Instant first = repository.block(blocker, blocked, FIRST);
		Instant repeated = repository.block(blocker, blocked, SECOND);

		assertEquals(FIRST, first);
		assertEquals(FIRST, repeated);
		assertEquals(1, blockCount(blocker, blocked));
		assertEquals(blocked,
			repository.findActiveProfileOwnerId(profileId).orElseThrow());
		assertTrue(repository.findActiveProfileOwnerId(999_999L).isEmpty());
	}

	@Test
	void resolvesOnlyActiveUsersAndUnblocksIdempotently() {
		long blocker = user("usr_blocker_active");
		long blocked = user("usr_blocked_active");
		long deleted = user("usr_blocker_deleted");
		long deletedProfileId = profile(deleted, "Deleted Buddy");
		jdbcTemplate.update("UPDATE users SET deleted_at = NOW(6) WHERE id = ?", deleted);
		repository.block(blocker, blocked, FIRST);

		assertEquals(blocker,
			repository.findActiveUserId("usr_blocker_active").orElseThrow());
		assertTrue(repository.findActiveUserId("usr_blocker_deleted").isEmpty());
		assertTrue(repository.findActiveUserId("usr_missing").isEmpty());
		assertTrue(repository.findActiveProfileOwnerId(deletedProfileId).isEmpty());

		repository.unblock(blocker, blocked);
		repository.unblock(blocker, blocked);

		assertEquals(0, blockCount(blocker, blocked));
	}

	@Test
	void databaseRejectsSelfBlocksAndUnknownUsers() {
		long userId = user("usr_block_constraint");

		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
			"""
			INSERT INTO buddy_blocks (blocker_user_id, blocked_user_id, created_at)
			VALUES (?, ?, NOW(6))
			""",
			userId,
			userId));
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
			"""
			INSERT INTO buddy_blocks (blocker_user_id, blocked_user_id, created_at)
			VALUES (?, ?, NOW(6))
			""",
			userId,
			Long.MAX_VALUE));
	}

	private long user(String publicId) {
		jdbcTemplate.update(
			"INSERT INTO users (public_id, signup_status) VALUES (?, 'COMPLETED')",
			publicId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private long profile(long userId, String nickname) {
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_profiles
			    (user_id, nickname, korean_level, profile_public, sns_public,
			     allows_messages, created_at, updated_at)
			VALUES (?, ?, 'BEGINNER', TRUE, FALSE, TRUE, NOW(6), NOW(6))
			""",
			userId,
			nickname);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM buddy_profiles WHERE user_id = ?", Long.class, userId);
	}

	private int blockCount(long blockerUserId, long blockedUserId) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*) FROM buddy_blocks
			WHERE blocker_user_id = ? AND blocked_user_id = ?
			""",
			Integer.class,
			blockerUserId,
			blockedUserId);
	}
}
