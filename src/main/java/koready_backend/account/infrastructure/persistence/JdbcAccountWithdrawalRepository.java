package koready_backend.account.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.account.application.port.AccountWithdrawalRepository;
import koready_backend.account.domain.AccountStatus;

@Repository
public class JdbcAccountWithdrawalRepository implements AccountWithdrawalRepository {

	private final JdbcTemplate jdbc;

	public JdbcAccountWithdrawalRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<WithdrawalState> find(String publicId) {
		return jdbc.query("""
			SELECT u.id, u.account_status, w.requested_at, w.scheduled_for,
			       w.confirmed_at, w.message_purge_at
			FROM users u LEFT JOIN account_withdrawals w ON w.user_id = u.id
			WHERE u.public_id = ? AND u.deleted_at IS NULL
			""", (rs, row) -> state(rs), publicId).stream().findFirst();
	}

	@Override
	public Optional<WithdrawalState> request(String publicId, Instant requestedAt, Instant scheduledFor) {
		var user = jdbc.query("""
			SELECT u.id, u.account_status, p.profile_public, p.sns_public, p.allows_messages
			FROM users u LEFT JOIN buddy_profiles p ON p.user_id = u.id
			WHERE u.public_id = ? AND u.deleted_at IS NULL FOR UPDATE
			""", (rs, row) -> new UserFlags(rs.getLong("id"),
			AccountStatus.valueOf(rs.getString("account_status")),
			(Boolean) rs.getObject("profile_public"), (Boolean) rs.getObject("sns_public"),
			(Boolean) rs.getObject("allows_messages")), publicId).stream().findFirst();
		if (user.isEmpty()) return Optional.empty();
		if (user.get().status() == AccountStatus.WITHDRAWN) return Optional.empty();
		if (user.get().status() == AccountStatus.WITHDRAWAL_PENDING) return find(publicId);
		Timestamp now = Timestamp.from(requestedAt);
		jdbc.update("""
			INSERT INTO account_withdrawals
			(user_id, status, requested_at, scheduled_for, previous_profile_public,
			 previous_sns_public, previous_allows_messages, updated_at)
			VALUES (?, 'PENDING', ?, ?, ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE status='PENDING', requested_at=VALUES(requested_at),
			 scheduled_for=VALUES(scheduled_for), confirmed_at=NULL, message_purge_at=NULL,
			 cancelled_at=NULL, previous_profile_public=VALUES(previous_profile_public),
			 previous_sns_public=VALUES(previous_sns_public),
			 previous_allows_messages=VALUES(previous_allows_messages), updated_at=VALUES(updated_at)
			""", user.get().id(), now, Timestamp.from(scheduledFor), user.get().profilePublic(),
			user.get().snsPublic(), user.get().allowsMessages(), now);
		jdbc.update("UPDATE users SET account_status='WITHDRAWAL_PENDING', updated_at=? WHERE id=?", now, user.get().id());
		jdbc.update("UPDATE buddy_profiles SET profile_public=FALSE, sns_public=FALSE, allows_messages=FALSE, updated_at=? WHERE user_id=?", now, user.get().id());
		jdbc.update("UPDATE auth_refresh_sessions SET revoked_at=COALESCE(revoked_at, ?) WHERE user_id=?", now, user.get().id());
		return find(publicId);
	}

	@Override
	public Optional<WithdrawalState> cancel(String publicId, Instant cancelledAt) {
		var row = jdbc.query("""
			SELECT u.id, w.scheduled_for, w.previous_profile_public, w.previous_sns_public,
			       w.previous_allows_messages
			FROM users u JOIN account_withdrawals w ON w.user_id=u.id
			WHERE u.public_id=? AND u.account_status='WITHDRAWAL_PENDING'
			  AND w.status='PENDING' FOR UPDATE
			""", (rs, n) -> new CancelRow(rs.getLong("id"), rs.getTimestamp("scheduled_for").toInstant(),
			(Boolean) rs.getObject("previous_profile_public"), (Boolean) rs.getObject("previous_sns_public"),
			(Boolean) rs.getObject("previous_allows_messages")), publicId).stream().findFirst();
		if (row.isEmpty() || !cancelledAt.isBefore(row.get().scheduledFor())) return Optional.empty();
		Timestamp now = Timestamp.from(cancelledAt);
		jdbc.update("UPDATE users SET account_status='ACTIVE', updated_at=? WHERE id=?", now, row.get().id());
		jdbc.update("""
			UPDATE buddy_profiles SET profile_public=COALESCE(?, FALSE), sns_public=COALESCE(?, FALSE),
			 allows_messages=COALESCE(?, FALSE), updated_at=? WHERE user_id=?
			""", row.get().profilePublic(), row.get().snsPublic(), row.get().allowsMessages(), now, row.get().id());
		jdbc.update("UPDATE account_withdrawals SET status='CANCELLED', cancelled_at=?, updated_at=? WHERE user_id=?", now, now, row.get().id());
		return find(publicId);
	}

	@Override
	public List<Long> findDueForConfirmation(Instant now, int limit) {
		return jdbc.query("SELECT user_id FROM account_withdrawals WHERE status='PENDING' AND scheduled_for<=? ORDER BY scheduled_for LIMIT ?",
			(rs, row) -> rs.getLong(1), Timestamp.from(now), limit);
	}

	@Override
	public List<String> findProfileImageKeys(long userId) {
		return jdbc.query("SELECT object_key FROM buddy_profile_images WHERE user_id=?", (rs, row) -> rs.getString(1), userId);
	}

	@Override
	public void confirm(long userId, Instant confirmedAt, Instant messagePurgeAt) {
		Timestamp now = Timestamp.from(confirmedAt);
		if (jdbc.update("UPDATE account_withdrawals SET status='CONFIRMED', confirmed_at=?, message_purge_at=?, updated_at=? WHERE user_id=? AND status='PENDING'",
			now, Timestamp.from(messagePurgeAt), now, userId) == 0) return;
		jdbc.update("UPDATE users SET default_location_id=NULL WHERE id=?", userId);
		delete("route_caches", userId); delete("user_place_events", userId);
		delete("user_place_recommendation_states", userId);
		jdbc.update("DELETE FROM recommendation_deck_pages WHERE deck_id IN (SELECT id FROM recommendation_decks WHERE user_id=?)", userId);
		jdbc.update("DELETE FROM recommendation_deck_items WHERE deck_id IN (SELECT id FROM recommendation_decks WHERE user_id=?)", userId);
		delete("recommendation_decks", userId); delete("user_onboarding_place_selections", userId);
		delete("user_saved_places", userId); delete("user_travel_styles", userId);
		delete("user_locations", userId); delete("user_term_agreements", userId);
		jdbc.update("DELETE FROM buddy_blocks WHERE blocker_user_id=? OR blocked_user_id=?", userId, userId);
		delete("user_social_identities", userId); delete("auth_refresh_sessions", userId);
		delete("buddy_profile_images", userId);
		jdbc.update("""
			UPDATE buddy_profiles SET profile_image_url=NULL, nickname='탈퇴한 사용자', nationality_code='ZZ',
			 bio=NULL, profile_public=FALSE, sns_public=FALSE, allows_messages=FALSE, updated_at=? WHERE user_id=?
			""", now, userId);
		jdbc.update("UPDATE users SET public_id=CONCAT('withdrawn_', id), preferred_language='KO', signup_status='NEED_TERMS', account_status='WITHDRAWN', onboarding_completed_at=NULL, updated_at=? WHERE id=?", now, userId);
	}

	@Override
	public List<Long> findDueForMessagePurge(Instant now, int limit) {
		return jdbc.query("SELECT user_id FROM account_withdrawals WHERE status='CONFIRMED' AND message_purge_at<=? ORDER BY message_purge_at LIMIT ?",
			(rs, row) -> rs.getLong(1), Timestamp.from(now), limit);
	}

	@Override
	public void purgeMessagesAndTombstone(long userId) {
		Long profileId = jdbc.query("SELECT id FROM buddy_profiles WHERE user_id=?", (rs, row) -> rs.getLong(1), userId)
			.stream().findFirst().orElse(null);
		if (profileId == null) return;
		jdbc.update("DELETE FROM buddy_reports WHERE reporter_user_id=? OR target_profile_id=? OR target_message_id IN (SELECT id FROM buddy_messages WHERE sender_profile_id=? OR receiver_profile_id=?)", userId, profileId, profileId, profileId);
		jdbc.update("DELETE FROM message_idempotency_keys WHERE sender_profile_id=? OR message_id IN (SELECT id FROM buddy_messages WHERE sender_profile_id=? OR receiver_profile_id=?)", profileId, profileId, profileId);
		jdbc.update("UPDATE buddy_message_threads SET last_message_id=NULL WHERE profile_low_id=? OR profile_high_id=?", profileId, profileId);
		jdbc.update("DELETE FROM buddy_messages WHERE sender_profile_id=? OR receiver_profile_id=?", profileId, profileId);
		jdbc.update("DELETE FROM buddy_message_threads WHERE profile_low_id=? OR profile_high_id=?", profileId, profileId);
		jdbc.update("DELETE FROM buddy_profiles WHERE id=?", profileId);
		jdbc.update("UPDATE account_withdrawals SET message_purge_at=NULL, updated_at=CURRENT_TIMESTAMP(6) WHERE user_id=?", userId);
	}

	private void delete(String table, long userId) {
		jdbc.update("DELETE FROM " + table + " WHERE user_id=?", userId);
	}

	private static WithdrawalState state(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new WithdrawalState(rs.getLong("id"), AccountStatus.valueOf(rs.getString("account_status")),
			instant(rs.getTimestamp("requested_at")), instant(rs.getTimestamp("scheduled_for")),
			instant(rs.getTimestamp("confirmed_at")), instant(rs.getTimestamp("message_purge_at")));
	}

	private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
	private record UserFlags(long id, AccountStatus status, Boolean profilePublic, Boolean snsPublic, Boolean allowsMessages) {}
	private record CancelRow(long id, Instant scheduledFor, Boolean profilePublic, Boolean snsPublic, Boolean allowsMessages) {}
}
