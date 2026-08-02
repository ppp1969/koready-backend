package koready_backend.terms.infrastructure.persistence;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.terms.application.port.TermsRepository;
import koready_backend.user.domain.SignupStatus;

@Repository
public class JdbcTermsRepository implements TermsRepository {

	private static final String CURRENT_TERMS_SQL = """
		WITH ranked_versions AS (
		    SELECT version.id,
		           version.term_id,
		           version.version_label,
		           version.title,
		           version.content_url,
		           version.required,
		           ROW_NUMBER() OVER (
		               PARTITION BY version.term_id
		               ORDER BY version.effective_at DESC, version.id DESC
		           ) AS version_rank
		    FROM term_versions version
		    JOIN term_definitions definition ON definition.id = version.term_id
		    WHERE definition.enabled = TRUE
		      AND version.published_at IS NOT NULL
		      AND version.published_at <= ?
		      AND version.effective_at <= ?
		      AND (version.withdrawn_at IS NULL OR version.withdrawn_at > ?)
		)
		SELECT definition.id AS term_id,
		       version.id AS term_version_id,
		       definition.code,
		       version.title,
		       version.required,
		       version.version_label,
		       version.content_url,
		       definition.display_order,
		       COALESCE(agreement.agreed, FALSE) AS agreed,
		       agreement.agreed_at
		FROM ranked_versions version
		JOIN term_definitions definition ON definition.id = version.term_id
		LEFT JOIN user_term_agreements agreement
		  ON agreement.term_version_id = version.id
		 AND agreement.user_id = ?
		WHERE version.version_rank = 1
		ORDER BY definition.display_order, definition.id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcTermsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<UserState> findActiveUser(String publicId) {
		return findUser(publicId, false);
	}

	@Override
	public Optional<UserState> findActiveUserForUpdate(String publicId) {
		return findUser(publicId, true);
	}

	@Override
	public List<CurrentTerm> findCurrentTerms(long userId, Instant asOf) {
		Timestamp timestamp = Timestamp.from(asOf);
		return jdbcTemplate.query(
			CURRENT_TERMS_SQL,
			this::mapCurrentTerm,
			timestamp,
			timestamp,
			timestamp,
			userId);
	}

	@Override
	public void saveAgreements(
		long userId,
		List<AgreementChange> agreements,
		Instant updatedAt
	) {
		for (AgreementChange agreement : agreements) {
			Timestamp agreedAt =
				agreement.agreed() ? Timestamp.from(updatedAt) : null;
			jdbcTemplate.update(
				"""
				INSERT INTO user_term_agreements
				    (user_id, term_version_id, agreed, agreed_at,
				     created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE
				    agreed_at = CASE
				        WHEN user_term_agreements.agreed = TRUE
				         AND VALUES(agreed) = TRUE
				            THEN user_term_agreements.agreed_at
				        WHEN VALUES(agreed) = TRUE
				            THEN VALUES(agreed_at)
				        ELSE NULL
				    END,
				    agreed = VALUES(agreed),
				    updated_at = VALUES(updated_at)
				""",
				userId,
				agreement.termVersionId(),
				agreement.agreed(),
				agreedAt,
				Timestamp.from(updatedAt),
				Timestamp.from(updatedAt));
		}
	}

	@Override
	public void updateSignupStatus(
		long userId,
		SignupStatus signupStatus,
		Instant updatedAt
	) {
		jdbcTemplate.update(
			"""
			UPDATE users
			SET signup_status = ?, updated_at = ?
			WHERE id = ? AND deleted_at IS NULL
			""",
			signupStatus.name(),
			Timestamp.from(updatedAt),
			userId);
	}

	private Optional<UserState> findUser(String publicId, boolean forUpdate) {
		String sql = """
			SELECT id, signup_status
			FROM users
			WHERE public_id = ? AND deleted_at IS NULL
			""" + (forUpdate ? " FOR UPDATE" : "");
		return jdbcTemplate.query(
			sql,
			(resultSet, rowNumber) -> new UserState(
				resultSet.getLong("id"),
				SignupStatus.valueOf(resultSet.getString("signup_status"))),
			publicId).stream().findFirst();
	}

	private CurrentTerm mapCurrentTerm(ResultSet resultSet, int rowNumber)
		throws SQLException {
		Timestamp agreedAt = resultSet.getTimestamp("agreed_at");
		return new CurrentTerm(
			resultSet.getLong("term_id"),
			resultSet.getLong("term_version_id"),
			resultSet.getString("code"),
			resultSet.getString("title"),
			resultSet.getBoolean("required"),
			resultSet.getString("version_label"),
			URI.create(resultSet.getString("content_url")),
			resultSet.getInt("display_order"),
			resultSet.getBoolean("agreed"),
			agreedAt == null ? null : agreedAt.toInstant());
	}
}
