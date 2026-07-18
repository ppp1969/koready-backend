package koready_backend.user.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.port.UserLanguageRepository;
import koready_backend.user.domain.SignupStatus;

@Repository
public class JdbcUserLanguageRepository implements UserLanguageRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcUserLanguageRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<UserLanguageState> findByPublicIdForUpdate(String publicId) {
		return jdbcTemplate.query(
			"""
			SELECT id, preferred_language, signup_status, updated_at
			FROM users
			WHERE public_id = ? AND deleted_at IS NULL
			FOR UPDATE
			""",
			this::map,
			publicId).stream().findFirst();
	}

	@Override
	public UserLanguageState update(
		long userId,
		PlaceLanguage language,
		SignupStatus signupStatus,
		Instant updatedAt
	) {
		jdbcTemplate.update(
			"""
			UPDATE users
			SET preferred_language = ?, signup_status = ?, updated_at = ?
			WHERE id = ? AND deleted_at IS NULL
			""",
			language.name(),
			signupStatus.name(),
			Timestamp.from(updatedAt),
			userId);
		return jdbcTemplate.queryForObject(
			"""
			SELECT id, preferred_language, signup_status, updated_at
			FROM users
			WHERE id = ? AND deleted_at IS NULL
			""",
			this::map,
			userId);
	}

	private UserLanguageState map(ResultSet resultSet, int rowNumber) throws SQLException {
		return new UserLanguageState(
			resultSet.getLong("id"),
			PlaceLanguage.valueOf(resultSet.getString("preferred_language")),
			SignupStatus.valueOf(resultSet.getString("signup_status")),
			resultSet.getTimestamp("updated_at").toInstant());
	}
}
