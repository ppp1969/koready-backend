package koready_backend.buddy.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

	@Override
	public List<ThreadSummaryRow> findThreads(ThreadListCriteria criteria) {
		String cursorCondition = criteria.cursor() == null
			? ""
			: """
			  AND (thread.updated_at < ?
			       OR (thread.updated_at = ? AND thread.id < ?))
			""";
		String sql = """
			SELECT thread.id AS thread_database_id,
			       thread.public_id AS thread_public_id,
			       thread.updated_at,
			       place.id AS place_id,
			       COALESCE(requested.title, fallback.title,
			                CONCAT('Place ', place.id)) AS place_title,
			       place.first_image_url,
			       other_profile.id AS other_profile_id,
			       other_profile.user_id AS other_user_id,
			       other_profile.nickname AS other_nickname,
			       other_profile.profile_image_url AS other_profile_image_url,
			       other_profile.profile_public AS other_profile_public,
			       other_profile.allows_messages AS other_allows_messages,
			       latest_message.content AS latest_content,
			       latest_message.sent_at AS last_sent_at,
			       (
			           SELECT COUNT(*)
			           FROM buddy_messages unread
			           WHERE unread.thread_id = thread.id
			             AND unread.receiver_profile_id = requester_profile.id
			             AND unread.read_at IS NULL
			             AND unread.deleted_by_receiver_at IS NULL
			       ) AS unread_count,
			       (
			           EXISTS (
			               SELECT 1
			               FROM buddy_blocks buddy_block
			               WHERE buddy_block.blocker_user_id = requester_profile.user_id
			                 AND buddy_block.blocked_user_id = other_profile.user_id
			           )
			           OR EXISTS (
			               SELECT 1
			               FROM buddy_blocks buddy_block
			               WHERE buddy_block.blocker_user_id = other_profile.user_id
			                 AND buddy_block.blocked_user_id = requester_profile.user_id
			           )
			       ) AS blocked
			FROM buddy_message_threads thread
			JOIN buddy_profiles requester_profile
			  ON requester_profile.id = ?
			JOIN buddy_profiles other_profile
			  ON other_profile.id = CASE
			       WHEN thread.profile_low_id = requester_profile.id
			       THEN thread.profile_high_id
			       ELSE thread.profile_low_id
			     END
			JOIN users other_owner
			  ON other_owner.id = other_profile.user_id
			 AND other_owner.deleted_at IS NULL
			JOIN places place ON place.id = thread.place_id
			LEFT JOIN place_localizations requested
			  ON requested.place_id = place.id AND requested.language = ?
			LEFT JOIN place_localizations fallback
			  ON fallback.place_id = place.id AND fallback.language = 'KO'
			JOIN buddy_messages latest_message
			  ON latest_message.id = (
			       SELECT visible.id
			       FROM buddy_messages visible
			       WHERE visible.thread_id = thread.id
			         AND (
			           (visible.sender_profile_id = requester_profile.id
			             AND visible.deleted_by_sender_at IS NULL)
			           OR
			           (visible.receiver_profile_id = requester_profile.id
			             AND visible.deleted_by_receiver_at IS NULL)
			         )
			       ORDER BY visible.id DESC
			       LIMIT 1
			     )
			WHERE (thread.profile_low_id = ? OR thread.profile_high_id = ?)
			""" + cursorCondition + """
			ORDER BY thread.updated_at DESC, thread.id DESC
			LIMIT ?
			""";
		List<Object> parameters = new ArrayList<>();
		parameters.add(criteria.requesterProfileId());
		parameters.add(criteria.language());
		parameters.add(criteria.requesterProfileId());
		parameters.add(criteria.requesterProfileId());
		if (criteria.cursor() != null) {
			parameters.add(Timestamp.from(criteria.cursor().updatedAt()));
			parameters.add(Timestamp.from(criteria.cursor().updatedAt()));
			parameters.add(criteria.cursor().threadDatabaseId());
		}
		parameters.add(criteria.limit());

		return jdbcTemplate.query(
			sql,
			(resultSet, rowNumber) -> new ThreadSummaryRow(
				resultSet.getLong("thread_database_id"),
				resultSet.getString("thread_public_id"),
				resultSet.getTimestamp("updated_at").toInstant(),
				new PlaceSnapshot(
					resultSet.getLong("place_id"),
					resultSet.getString("place_title"),
					resultSet.getString("first_image_url")),
				new MessageProfile(
					resultSet.getLong("other_profile_id"),
					resultSet.getLong("other_user_id"),
					resultSet.getString("other_nickname"),
					resultSet.getString("other_profile_image_url"),
					resultSet.getBoolean("other_profile_public"),
					resultSet.getBoolean("other_allows_messages")),
				resultSet.getString("latest_content"),
				resultSet.getTimestamp("last_sent_at").toInstant(),
				resultSet.getLong("unread_count"),
				resultSet.getBoolean("blocked")),
			parameters.toArray());
	}

	@Override
	public long countUnreadMessages(long receiverProfileId) {
		Long count = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM buddy_messages message
			JOIN buddy_profiles sender_profile
			  ON sender_profile.id = message.sender_profile_id
			JOIN users sender_owner
			  ON sender_owner.id = sender_profile.user_id
			 AND sender_owner.deleted_at IS NULL
			WHERE message.receiver_profile_id = ?
			  AND message.read_at IS NULL
			  AND message.deleted_by_receiver_at IS NULL
			""",
			Long.class,
			receiverProfileId);
		return count == null ? 0 : count;
	}

	@Override
	public Optional<ThreadContext> findThreadContext(
		String threadPublicId,
		long requesterProfileId,
		String language
	) {
		return jdbcTemplate.query(
			"""
			SELECT thread.id, thread.public_id, thread.place_id,
			       thread.profile_low_id, thread.profile_high_id,
			       COALESCE(requested.title, fallback.title,
			                CONCAT('Place ', place.id)) AS place_title,
			       place.first_image_url,
			       other_profile.id AS other_profile_id,
			       other_profile.user_id AS other_user_id,
			       other_profile.nickname AS other_nickname,
			       other_profile.profile_image_url AS other_profile_image_url,
			       other_profile.profile_public AS other_profile_public,
			       other_profile.allows_messages AS other_allows_messages,
			       EXISTS (
			           SELECT 1
			           FROM buddy_blocks buddy_block
			           WHERE buddy_block.blocker_user_id = requester_profile.user_id
			             AND buddy_block.blocked_user_id = other_profile.user_id
			       ) AS blocked_by_requester,
			       EXISTS (
			           SELECT 1
			           FROM buddy_blocks buddy_block
			           WHERE buddy_block.blocker_user_id = other_profile.user_id
			             AND buddy_block.blocked_user_id = requester_profile.user_id
			       ) AS blocked_by_target
			FROM buddy_message_threads thread
			JOIN buddy_profiles requester_profile
			  ON requester_profile.id = ?
			JOIN buddy_profiles other_profile
			  ON other_profile.id = CASE
			       WHEN thread.profile_low_id = requester_profile.id
			       THEN thread.profile_high_id
			       ELSE thread.profile_low_id
			     END
			JOIN users other_owner
			  ON other_owner.id = other_profile.user_id
			 AND other_owner.deleted_at IS NULL
			JOIN places place ON place.id = thread.place_id
			LEFT JOIN place_localizations requested
			  ON requested.place_id = place.id AND requested.language = ?
			LEFT JOIN place_localizations fallback
			  ON fallback.place_id = place.id AND fallback.language = 'KO'
			WHERE thread.public_id = ?
			  AND (thread.profile_low_id = ? OR thread.profile_high_id = ?)
			""",
			(resultSet, rowNumber) -> new ThreadContext(
				thread(resultSet),
				new PlaceSnapshot(
					resultSet.getLong("place_id"),
					resultSet.getString("place_title"),
					resultSet.getString("first_image_url")),
				new MessageProfile(
					resultSet.getLong("other_profile_id"),
					resultSet.getLong("other_user_id"),
					resultSet.getString("other_nickname"),
					resultSet.getString("other_profile_image_url"),
					resultSet.getBoolean("other_profile_public"),
					resultSet.getBoolean("other_allows_messages")),
				resultSet.getBoolean("blocked_by_requester"),
				resultSet.getBoolean("blocked_by_target")),
			requesterProfileId,
			language,
			threadPublicId,
			requesterProfileId,
			requesterProfileId).stream().findFirst();
	}

	@Override
	public List<StoredMessage> findMessages(MessagePageCriteria criteria) {
		String cursorCondition = criteria.beforeMessageId() == null
			? ""
			: " AND message.id < ?\n";
		String sql = """
			SELECT message.id, message.sender_profile_id,
			       message.receiver_profile_id, thread.place_id,
			       message.content, message.sent_at, message.read_at
			FROM buddy_messages message
			JOIN buddy_message_threads thread ON thread.id = message.thread_id
			WHERE message.thread_id = ?
			  AND (
			    (message.sender_profile_id = ?
			      AND message.deleted_by_sender_at IS NULL)
			    OR
			    (message.receiver_profile_id = ?
			      AND message.deleted_by_receiver_at IS NULL)
			  )
			""" + cursorCondition + """
			ORDER BY message.id DESC
			LIMIT ?
			""";
		List<Object> parameters = new ArrayList<>();
		parameters.add(criteria.threadDatabaseId());
		parameters.add(criteria.viewerProfileId());
		parameters.add(criteria.viewerProfileId());
		if (criteria.beforeMessageId() != null) {
			parameters.add(criteria.beforeMessageId());
		}
		parameters.add(criteria.limit());

		return jdbcTemplate.query(
			sql,
			(resultSet, rowNumber) -> new StoredMessage(
				resultSet.getLong("id"),
				criteria.threadPublicId(),
				resultSet.getLong("sender_profile_id"),
				resultSet.getLong("receiver_profile_id"),
				resultSet.getLong("place_id"),
				resultSet.getString("content"),
				resultSet.getTimestamp("sent_at").toInstant(),
				optionalInstant(resultSet.getTimestamp("read_at"))),
			parameters.toArray());
	}

	@Override
	public Instant markRead(
		MessageThread thread,
		long receiverProfileId,
		Instant readAt
	) {
		jdbcTemplate.update(
			"""
			UPDATE buddy_messages
			SET read_at = ?
			WHERE thread_id = ?
			  AND receiver_profile_id = ?
			  AND read_at IS NULL
			  AND deleted_by_receiver_at IS NULL
			""",
			Timestamp.from(readAt),
			thread.databaseId(),
			receiverProfileId);
		Timestamp latestReadAt = jdbcTemplate.queryForObject(
			"""
			SELECT MAX(read_at)
			FROM buddy_messages
			WHERE thread_id = ? AND receiver_profile_id = ?
			""",
			Timestamp.class,
			thread.databaseId(),
			receiverProfileId);
		return latestReadAt == null ? readAt : latestReadAt.toInstant();
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
