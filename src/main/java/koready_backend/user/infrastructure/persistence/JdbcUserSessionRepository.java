package koready_backend.user.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.port.UserSessionRepository;
import koready_backend.user.domain.SignupStatus;

@Repository
public class JdbcUserSessionRepository implements UserSessionRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcUserSessionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<UserSessionRecord> find(String publicId, Instant asOf) {
		Timestamp now = Timestamp.from(asOf);
		return jdbcTemplate.query(
			"""
			SELECT owner.id,
			       owner.public_id,
			       identity.email,
			       profile.profile_image_url,
			       owner.preferred_language,
			       owner.signup_status,
			       owner.default_location_id,
			       owner.onboarding_completed_at IS NOT NULL AS onboarding_completed,
			       profile.id IS NOT NULL AS buddy_profile_exists,
			       (SELECT COUNT(*)
			        FROM buddy_messages message
			        WHERE message.receiver_profile_id = profile.id
			          AND message.read_at IS NULL
			          AND message.deleted_by_receiver_at IS NULL) AS unread_message_count,
			       EXISTS (
			           SELECT 1
			           FROM term_definitions definition
			           JOIN term_versions version ON version.term_id = definition.id
			           WHERE definition.enabled = TRUE
			             AND version.required = TRUE
			             AND version.published_at IS NOT NULL
			             AND version.published_at <= ?
			             AND version.effective_at <= ?
			             AND (version.withdrawn_at IS NULL OR version.withdrawn_at > ?)
			             AND NOT EXISTS (
			                 SELECT 1 FROM term_versions newer
			                 WHERE newer.term_id = version.term_id
			                   AND newer.published_at IS NOT NULL
			                   AND newer.published_at <= ?
			                   AND newer.effective_at <= ?
			                   AND (newer.withdrawn_at IS NULL OR newer.withdrawn_at > ?)
			                   AND (newer.effective_at > version.effective_at
			                        OR (newer.effective_at = version.effective_at
			                            AND newer.id > version.id))
			             )
			             AND NOT EXISTS (
			                 SELECT 1 FROM user_term_agreements agreement
			                 WHERE agreement.user_id = owner.id
			                   AND agreement.term_version_id = version.id
			                   AND agreement.agreed = TRUE
			             )
			       ) AS terms_need_reagreement
			FROM users owner
			JOIN user_social_identities identity
			  ON identity.user_id = owner.id AND identity.provider = 'GOOGLE'
			LEFT JOIN buddy_profiles profile ON profile.user_id = owner.id
			WHERE owner.public_id = ? AND owner.deleted_at IS NULL
			""",
			(resultSet, rowNumber) -> map(resultSet),
			now, now, now, now, now, now, publicId).stream().findFirst();
	}

	private static UserSessionRecord map(ResultSet resultSet) throws SQLException {
		return new UserSessionRecord(
			resultSet.getLong("id"), resultSet.getString("public_id"),
			resultSet.getString("email"), resultSet.getString("profile_image_url"),
			PlaceLanguage.valueOf(resultSet.getString("preferred_language")),
			SignupStatus.valueOf(resultSet.getString("signup_status")),
			nullableLong(resultSet, "default_location_id"),
			resultSet.getBoolean("onboarding_completed"),
			resultSet.getBoolean("buddy_profile_exists"),
			resultSet.getLong("unread_message_count"),
			resultSet.getBoolean("terms_need_reagreement"));
	}

	private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}
}
