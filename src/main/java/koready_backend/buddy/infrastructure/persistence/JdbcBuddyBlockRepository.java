package koready_backend.buddy.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.buddy.application.port.BuddyBlockRepository;

@Repository
public class JdbcBuddyBlockRepository implements BuddyBlockRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcBuddyBlockRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<Long> findActiveUserId(String publicId) {
		return jdbcTemplate.query(
			"SELECT id FROM users WHERE public_id = ? AND deleted_at IS NULL FOR UPDATE",
			(resultSet, rowNumber) -> resultSet.getLong("id"),
			publicId).stream().findFirst();
	}

	@Override
	public Optional<Long> findActiveProfileOwnerId(long profileId) {
		return jdbcTemplate.query(
			"""
			SELECT profile.user_id
			FROM buddy_profiles profile
			JOIN users owner ON owner.id = profile.user_id
			WHERE profile.id = ? AND owner.deleted_at IS NULL
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> resultSet.getLong("user_id"),
			profileId).stream().findFirst();
	}

	@Override
	public Instant block(long blockerUserId, long blockedUserId, Instant blockedAt) {
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_blocks (blocker_user_id, blocked_user_id, created_at)
			VALUES (?, ?, ?)
			ON DUPLICATE KEY UPDATE blocker_user_id = VALUES(blocker_user_id)
			""",
			blockerUserId,
			blockedUserId,
			Timestamp.from(blockedAt));
		return jdbcTemplate.queryForObject(
			"""
			SELECT created_at FROM buddy_blocks
			WHERE blocker_user_id = ? AND blocked_user_id = ?
			""",
			(resultSet, rowNumber) -> resultSet.getTimestamp("created_at").toInstant(),
			blockerUserId,
			blockedUserId);
	}

	@Override
	public void unblock(long blockerUserId, long blockedUserId) {
		jdbcTemplate.update(
			"""
			DELETE FROM buddy_blocks
			WHERE blocker_user_id = ? AND blocked_user_id = ?
			""",
			blockerUserId,
			blockedUserId);
	}
}
