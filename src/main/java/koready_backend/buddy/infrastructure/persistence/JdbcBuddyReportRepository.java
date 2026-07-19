package koready_backend.buddy.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.buddy.application.port.BuddyReportRepository;
import koready_backend.buddy.domain.ReportStatus;
import koready_backend.buddy.domain.ReportTargetType;

@Repository
public class JdbcBuddyReportRepository implements BuddyReportRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcBuddyReportRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<Long> findActiveReporterUserId(String publicId) {
		return jdbcTemplate.query(
			"SELECT id FROM users WHERE public_id = ? AND deleted_at IS NULL",
			(resultSet, rowNumber) -> resultSet.getLong("id"),
			publicId).stream().findFirst();
	}

	@Override
	public Optional<ProfileTarget> findActiveProfileTarget(long profileId) {
		return jdbcTemplate.query(
			"""
			SELECT profile.id, profile.user_id
			FROM buddy_profiles profile
			JOIN users owner
			  ON owner.id = profile.user_id AND owner.deleted_at IS NULL
			WHERE profile.id = ?
			""",
			(resultSet, rowNumber) -> new ProfileTarget(
				resultSet.getLong("id"),
				resultSet.getLong("user_id")),
			profileId).stream().findFirst();
	}

	@Override
	public Optional<MessageTarget> findReceivedMessageTarget(
		long messageId,
		long reporterUserId
	) {
		return jdbcTemplate.query(
			"""
			SELECT message.id, message.sender_profile_id
			FROM buddy_messages message
			JOIN buddy_profiles receiver
			  ON receiver.id = message.receiver_profile_id
			WHERE message.id = ? AND receiver.user_id = ?
			""",
			(resultSet, rowNumber) -> new MessageTarget(
				resultSet.getLong("id"),
				resultSet.getLong("sender_profile_id")),
			messageId,
			reporterUserId).stream().findFirst();
	}

	@Override
	public Optional<StoredReport> findByIdempotencyKey(
		long reporterUserId,
		String idempotencyKey
	) {
		return jdbcTemplate.query(
			"""
			SELECT id, target_type,
			       CAST(CASE
			         WHEN target_type = 'PROFILE' THEN target_profile_id
			         ELSE target_message_id
			       END AS CHAR) AS target_id,
			       status, created_at, request_hash
			FROM buddy_reports
			WHERE reporter_user_id = ? AND idempotency_key = ?
			""",
			(resultSet, rowNumber) -> new StoredReport(
				resultSet.getLong("id"),
				ReportTargetType.valueOf(resultSet.getString("target_type")),
				resultSet.getString("target_id"),
				ReportStatus.valueOf(resultSet.getString("status")),
				resultSet.getTimestamp("created_at").toInstant(),
				resultSet.getString("request_hash")),
			reporterUserId,
			idempotencyKey).stream().findFirst();
	}

	@Override
	public StoredReport save(NewReport report) {
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(
				"""
				INSERT INTO buddy_reports
				    (reporter_user_id, target_type, target_profile_id,
				     target_message_id, reason, status, idempotency_key,
				     request_hash, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'RECEIVED', ?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE id = id
				""");
			statement.setLong(1, report.reporterUserId());
			statement.setString(2, report.targetType().name());
			statement.setLong(3, report.targetProfileId());
			if (report.targetMessageId() == null) {
				statement.setNull(4, java.sql.Types.BIGINT);
			} else {
				statement.setLong(4, report.targetMessageId());
			}
			statement.setString(5, report.reason());
			statement.setString(6, report.idempotencyKey());
			statement.setString(7, report.requestHash());
			statement.setTimestamp(8, Timestamp.from(report.createdAt()));
			statement.setTimestamp(9, Timestamp.from(report.createdAt()));
			return statement;
		});
		return findByIdempotencyKey(
			report.reporterUserId(), report.idempotencyKey())
			.orElseThrow(() -> new IllegalStateException(
				"Report could not be stored or replayed"));
	}
}
