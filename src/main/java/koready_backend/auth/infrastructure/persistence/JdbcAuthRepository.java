package koready_backend.auth.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import koready_backend.auth.application.port.AuthRepository;
import koready_backend.auth.domain.AuthUser;
import koready_backend.auth.domain.GoogleIdentity;
import koready_backend.auth.domain.RefreshSession;
import koready_backend.auth.domain.UserRole;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.domain.SignupStatus;

@Repository
public class JdbcAuthRepository implements AuthRepository {

	private static final String USER_SELECT = """
		SELECT owner.id,
		       owner.public_id,
		       identity.email,
		       owner.role,
		       owner.preferred_language,
		       owner.signup_status,
		       profile.profile_image_url
		FROM users owner
		JOIN user_social_identities identity ON identity.user_id = owner.id
		LEFT JOIN buddy_profiles profile ON profile.user_id = owner.id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcAuthRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<AuthUser> findByGoogleSubject(String providerSubject) {
		List<AuthUser> users = jdbcTemplate.query(
			USER_SELECT + """
				WHERE identity.provider = 'GOOGLE'
				  AND identity.provider_subject = ?
				  AND owner.deleted_at IS NULL
				FOR UPDATE
				""",
			this::mapUser,
			providerSubject);
		return users.stream().findFirst();
	}

	@Override
	public AuthUser createGoogleUser(
		GoogleIdentity identity,
		String userPublicId,
		Instant now
	) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO users
				    (public_id, preferred_language, signup_status,
				     created_at, updated_at)
				VALUES (?, 'KO', 'NEED_TERMS', ?, ?)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, userPublicId);
			statement.setTimestamp(2, Timestamp.from(now));
			statement.setTimestamp(3, Timestamp.from(now));
			return statement;
		}, keyHolder);
		Number generatedKey = keyHolder.getKey();
		if (generatedKey == null) {
			throw new IllegalStateException("User ID was not generated.");
		}
		long userId = generatedKey.longValue();
		jdbcTemplate.update(
			"""
			INSERT INTO user_social_identities
			    (user_id, provider, provider_subject, email, email_verified,
			     last_login_at, created_at, updated_at)
			VALUES (?, 'GOOGLE', ?, ?, TRUE, ?, ?, ?)
			""",
			userId,
			identity.providerSubject(),
			identity.email(),
			Timestamp.from(now),
			Timestamp.from(now),
			Timestamp.from(now));
		return findActiveUser(userId).orElseThrow();
	}

	@Override
	public AuthUser updateGoogleIdentity(
		long userId,
		GoogleIdentity identity,
		Instant now
	) {
		jdbcTemplate.update(
			"""
			UPDATE user_social_identities
			SET email = ?,
			    email_verified = TRUE,
			    last_login_at = ?,
			    updated_at = ?
			WHERE user_id = ?
			  AND provider = 'GOOGLE'
			  AND provider_subject = ?
			""",
			identity.email(),
			Timestamp.from(now),
			Timestamp.from(now),
			userId,
			identity.providerSubject());
		return findActiveUser(userId).orElseThrow();
	}

	@Override
	public Optional<AuthUser> findActiveUser(long userId) {
		List<AuthUser> users = jdbcTemplate.query(
			USER_SELECT + """
				WHERE owner.id = ?
				  AND owner.deleted_at IS NULL
				  AND identity.provider = 'GOOGLE'
				""",
			this::mapUser,
			userId);
		return users.stream().findFirst();
	}

	@Override
	public void saveRefreshSession(
		long userId,
		String tokenHash,
		String deviceIdHash,
		Instant createdAt,
		Instant expiresAt
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO auth_refresh_sessions
			    (user_id, token_hash, device_id_hash, expires_at,
			     revoked_at, created_at, updated_at)
			VALUES (?, ?, ?, ?, NULL, ?, ?)
			""",
			userId,
			tokenHash,
			deviceIdHash,
			Timestamp.from(expiresAt),
			Timestamp.from(createdAt),
			Timestamp.from(createdAt));
	}

	@Override
	public void revokeActiveRefreshSessions(
		long userId,
		String deviceIdHash,
		Instant revokedAt
	) {
		jdbcTemplate.update(
			"""
			UPDATE auth_refresh_sessions
			SET revoked_at = ?,
			    updated_at = ?
			WHERE user_id = ?
			  AND device_id_hash = ?
			  AND revoked_at IS NULL
			""",
			Timestamp.from(revokedAt),
			Timestamp.from(revokedAt),
			userId,
			deviceIdHash);
	}

	@Override
	public Optional<RefreshSession> findRefreshSessionForUpdate(String tokenHash) {
		List<RefreshSession> sessions = jdbcTemplate.query(
			"""
			SELECT id, user_id, token_hash, device_id_hash, expires_at, revoked_at
			FROM auth_refresh_sessions
			WHERE token_hash = ?
			FOR UPDATE
			""",
			this::mapSession,
			tokenHash);
		return sessions.stream().findFirst();
	}

	@Override
	public void revokeRefreshSession(long sessionId, Instant revokedAt) {
		jdbcTemplate.update(
			"""
			UPDATE auth_refresh_sessions
			SET revoked_at = ?,
			    updated_at = ?
			WHERE id = ?
			  AND revoked_at IS NULL
			""",
			Timestamp.from(revokedAt),
			Timestamp.from(revokedAt),
			sessionId);
	}

	private AuthUser mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
		return new AuthUser(
			resultSet.getLong("id"),
			resultSet.getString("public_id"),
			resultSet.getString("email"),
			UserRole.valueOf(resultSet.getString("role")),
			PlaceLanguage.valueOf(resultSet.getString("preferred_language")),
			SignupStatus.valueOf(resultSet.getString("signup_status")),
			resultSet.getString("profile_image_url"));
	}

	private RefreshSession mapSession(ResultSet resultSet, int rowNumber)
		throws SQLException {
		Timestamp revokedAt = resultSet.getTimestamp("revoked_at");
		return new RefreshSession(
			resultSet.getLong("id"),
			resultSet.getLong("user_id"),
			resultSet.getString("token_hash"),
			resultSet.getString("device_id_hash"),
			resultSet.getTimestamp("expires_at").toInstant(),
			revokedAt == null ? null : revokedAt.toInstant());
	}
}
