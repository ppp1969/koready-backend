package koready_backend.buddy.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import koready_backend.buddy.application.port.BuddyMessageRepository;

@Repository
public class JdbcBuddyMessageRepository implements BuddyMessageRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcBuddyMessageRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<ActiveUser> findActiveUser(String publicId) {
		return jdbcTemplate.query(
			"""
			SELECT id, COALESCE(preferred_language, 'KO') AS preferred_language
			FROM users
			WHERE public_id = ? AND deleted_at IS NULL
			""",
			(resultSet, rowNumber) -> new ActiveUser(
				resultSet.getLong("id"),
				resultSet.getString("preferred_language")),
			publicId).stream().findFirst();
	}

	@Override
	public Optional<MessageProfile> findProfileByUserId(long userId) {
		return jdbcTemplate.query(
			"""
			SELECT id, user_id, nickname, profile_image_url,
			       profile_public, allows_messages
			FROM buddy_profiles
			WHERE user_id = ?
			""",
			(resultSet, rowNumber) -> profile(resultSet),
			userId).stream().findFirst();
	}

	@Override
	public Optional<MessageProfile> findActiveProfile(long profileId) {
		return jdbcTemplate.query(
			"""
			SELECT profile.id, profile.user_id, profile.nickname,
			       profile.profile_image_url, profile.profile_public,
			       profile.allows_messages
			FROM buddy_profiles profile
			JOIN users owner ON owner.id = profile.user_id
			WHERE profile.id = ? AND owner.deleted_at IS NULL
			""",
			(resultSet, rowNumber) -> profile(resultSet),
			profileId).stream().findFirst();
	}

	@Override
	public Optional<PlaceSnapshot> findActivePlace(long placeId, String language) {
		return jdbcTemplate.query(
			"""
			SELECT place.id,
			       COALESCE(requested.title, fallback.title) AS title,
			       place.first_image_url
			FROM places place
			LEFT JOIN place_localizations requested
			  ON requested.place_id = place.id AND requested.language = ?
			LEFT JOIN place_localizations fallback
			  ON fallback.place_id = place.id AND fallback.language = 'KO'
			WHERE place.id = ?
			  AND place.active = TRUE
			  AND place.show_flag = TRUE
			  AND COALESCE(requested.title, fallback.title) IS NOT NULL
			""",
			(resultSet, rowNumber) -> new PlaceSnapshot(
				resultSet.getLong("id"),
				resultSet.getString("title"),
				resultSet.getString("first_image_url")),
			language,
			placeId).stream().findFirst();
	}

	@Override
	public Optional<MessageThread> findParticipantThread(
		String threadPublicId,
		long profileId
	) {
		return jdbcTemplate.query(
			"""
			SELECT id, public_id, place_id, profile_low_id, profile_high_id
			FROM buddy_message_threads
			WHERE public_id = ?
			  AND (profile_low_id = ? OR profile_high_id = ?)
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> thread(resultSet),
			threadPublicId,
			profileId,
			profileId).stream().findFirst();
	}

	@Override
	public IdempotencyClaim claimIdempotency(
		long senderProfileId,
		String idempotencyKey,
		String requestHash,
		Instant claimedAt
	) {
		int inserted = jdbcTemplate.update(
			"""
			INSERT IGNORE INTO message_idempotency_keys
			    (sender_profile_id, idempotency_key, request_hash, message_id, created_at)
			VALUES (?, ?, ?, NULL, ?)
			""",
			senderProfileId,
			idempotencyKey,
			requestHash,
			Timestamp.from(claimedAt));
		if (inserted == 1) {
			return IdempotencyClaim.claimed();
		}

		return jdbcTemplate.queryForObject(
			"""
			SELECT request_hash, message_id
			FROM message_idempotency_keys
			WHERE sender_profile_id = ? AND idempotency_key = ?
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> {
				if (!requestHash.equals(resultSet.getString("request_hash"))) {
					return IdempotencyClaim.conflict();
				}
				Long messageId = resultSet.getObject("message_id", Long.class);
				if (messageId == null) {
					throw new IllegalStateException(
						"Idempotency reservation was not completed");
				}
				return IdempotencyClaim.replay(messageId);
			},
			senderProfileId,
			idempotencyKey);
	}

	@Override
	public MessageThread findOrCreateThread(
		long placeId,
		long firstProfileId,
		long secondProfileId,
		String newPublicId,
		Instant createdAt
	) {
		long low = Math.min(firstProfileId, secondProfileId);
		long high = Math.max(firstProfileId, secondProfileId);
		jdbcTemplate.update(
			"""
			INSERT IGNORE INTO buddy_message_threads
			    (public_id, place_id, profile_low_id, profile_high_id,
			     last_message_id, created_at, updated_at)
			VALUES (?, ?, ?, ?, NULL, ?, ?)
			""",
			newPublicId,
			placeId,
			low,
			high,
			Timestamp.from(createdAt),
			Timestamp.from(createdAt));

		return jdbcTemplate.queryForObject(
			"""
			SELECT id, public_id, place_id, profile_low_id, profile_high_id
			FROM buddy_message_threads
			WHERE place_id = ? AND profile_low_id = ? AND profile_high_id = ?
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> thread(resultSet),
			placeId,
			low,
			high);
	}

	@Override
	public StoredMessage appendMessage(
		MessageThread thread,
		long senderProfileId,
		long receiverProfileId,
		String content,
		Instant sentAt
	) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(
				"""
				INSERT INTO buddy_messages
				    (thread_id, sender_profile_id, receiver_profile_id, content, sent_at)
				VALUES (?, ?, ?, ?, ?)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setLong(1, thread.databaseId());
			statement.setLong(2, senderProfileId);
			statement.setLong(3, receiverProfileId);
			statement.setString(4, content);
			statement.setTimestamp(5, Timestamp.from(sentAt));
			return statement;
		}, keyHolder);
		long messageId = keyHolder.getKey().longValue();
		int updated = jdbcTemplate.update(
			"""
			UPDATE buddy_message_threads
			SET last_message_id = ?, updated_at = ?
			WHERE id = ?
			""",
			messageId,
			Timestamp.from(sentAt),
			thread.databaseId());
		if (updated != 1) {
			throw new IllegalStateException("Message thread disappeared while sending");
		}
		return new StoredMessage(
			messageId,
			thread.publicId(),
			senderProfileId,
			receiverProfileId,
			thread.placeId(),
			content,
			sentAt,
			null);
	}

	@Override
	public void completeIdempotency(
		long senderProfileId,
		String idempotencyKey,
		long messageId
	) {
		int updated = jdbcTemplate.update(
			"""
			UPDATE message_idempotency_keys
			SET message_id = ?
			WHERE sender_profile_id = ?
			  AND idempotency_key = ?
			  AND message_id IS NULL
			""",
			messageId,
			senderProfileId,
			idempotencyKey);
		if (updated != 1) {
			throw new IllegalStateException("Idempotency reservation could not be completed");
		}
	}

	@Override
	public Optional<StoredMessage> findMessage(long messageId) {
		return jdbcTemplate.query(
			"""
			SELECT message.id, thread.public_id, message.sender_profile_id,
			       message.receiver_profile_id, thread.place_id, message.content,
			       message.sent_at, message.read_at
			FROM buddy_messages message
			JOIN buddy_message_threads thread ON thread.id = message.thread_id
			WHERE message.id = ?
			""",
			(resultSet, rowNumber) -> new StoredMessage(
				resultSet.getLong("id"),
				resultSet.getString("public_id"),
				resultSet.getLong("sender_profile_id"),
				resultSet.getLong("receiver_profile_id"),
				resultSet.getLong("place_id"),
				resultSet.getString("content"),
				resultSet.getTimestamp("sent_at").toInstant(),
				optionalInstant(resultSet.getTimestamp("read_at"))),
			messageId).stream().findFirst();
	}

	private static MessageProfile profile(java.sql.ResultSet resultSet)
		throws java.sql.SQLException {
		return new MessageProfile(
			resultSet.getLong("id"),
			resultSet.getLong("user_id"),
			resultSet.getString("nickname"),
			resultSet.getString("profile_image_url"),
			resultSet.getBoolean("profile_public"),
			resultSet.getBoolean("allows_messages"));
	}

	private static MessageThread thread(java.sql.ResultSet resultSet)
		throws java.sql.SQLException {
		return new MessageThread(
			resultSet.getLong("id"),
			resultSet.getString("public_id"),
			resultSet.getLong("place_id"),
			resultSet.getLong("profile_low_id"),
			resultSet.getLong("profile_high_id"));
	}

	private static Instant optionalInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}
}
