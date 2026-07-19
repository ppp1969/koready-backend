package koready_backend.onboarding.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.onboarding.application.port.OnboardingProfileRepository;
import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.user.domain.SignupStatus;

@Repository
public class JdbcOnboardingProfileRepository implements OnboardingProfileRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcOnboardingProfileRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<UserRecord> findUserByPublicId(String publicId) {
		return findUser(publicId, false);
	}

	@Override
	public Optional<UserRecord> findUserByPublicIdForUpdate(String publicId) {
		return findUser(publicId, true);
	}

	@Override
	public Optional<LocationRecord> findOwnedLocation(long userId, long locationId) {
		return jdbcTemplate.query(
			"""
			SELECT id, display_name, service_region_code, deleted_at
			FROM user_locations
			WHERE id = ? AND user_id = ?
			""",
			this::mapLocation,
			locationId,
			userId).stream().findFirst();
	}

	@Override
	public List<TravelStyle> findTravelStyles(long userId) {
		return jdbcTemplate.query(
			"""
			SELECT travel_style
			FROM user_travel_styles
			WHERE user_id = ?
			ORDER BY display_order
			""",
			(resultSet, rowNumber) -> TravelStyle.valueOf(
				resultSet.getString("travel_style")),
			userId);
	}

	@Override
	public Optional<CandidateSetRecord> findCandidateSet(String publicId) {
		return jdbcTemplate.query(
			"""
			SELECT id, public_id, version, status, published_at
			FROM onboarding_candidate_sets
			WHERE public_id = ?
			""",
			this::mapCandidateSet,
			publicId).stream().findFirst();
	}

	@Override
	public Set<Long> findCandidatePlaceIds(long candidateSetId) {
		List<Long> placeIds = jdbcTemplate.queryForList(
			"""
			SELECT place_id
			FROM onboarding_candidate_set_items
			WHERE candidate_set_id = ?
			ORDER BY display_order
			""",
			Long.class,
			candidateSetId);
		return new LinkedHashSet<>(placeIds);
	}

	@Override
	public Optional<SelectionRecord> findSelection(long userId) {
		List<SelectionRow> rows = jdbcTemplate.query(
			"""
			SELECT s.candidate_set_id, c.public_id, c.version,
			       s.place_id, s.selected_order
			FROM user_onboarding_place_selections s
			JOIN onboarding_candidate_sets c ON c.id = s.candidate_set_id
			WHERE s.user_id = ?
			ORDER BY s.selected_order
			""",
			this::mapSelection,
			userId);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		SelectionRow first = rows.getFirst();
		return Optional.of(new SelectionRecord(
			first.candidateSetId(),
			first.candidateSetPublicId(),
			first.candidateSetVersion(),
			rows.stream().map(SelectionRow::placeId).toList()));
	}

	@Override
	public void replaceTravelStyles(long userId, List<TravelStyle> styles, Instant now) {
		jdbcTemplate.update("DELETE FROM user_travel_styles WHERE user_id = ?", userId);
		for (int index = 0; index < styles.size(); index++) {
			jdbcTemplate.update(
				"""
				INSERT INTO user_travel_styles
				    (user_id, travel_style, display_order, created_at)
				VALUES (?, ?, ?, ?)
				""",
				userId,
				styles.get(index).name(),
				index + 1,
				timestamp(now));
		}
	}

	@Override
	public void replaceSelections(
		long userId,
		long candidateSetId,
		List<Long> placeIds,
		Instant now
	) {
		jdbcTemplate.update(
			"DELETE FROM user_onboarding_place_selections WHERE user_id = ?",
			userId);
		for (int index = 0; index < placeIds.size(); index++) {
			jdbcTemplate.update(
				"""
				INSERT INTO user_onboarding_place_selections
				    (user_id, candidate_set_id, place_id, selected_order, selected_at)
				VALUES (?, ?, ?, ?, ?)
				""",
				userId,
				candidateSetId,
				placeIds.get(index),
				index + 1,
				timestamp(now));
		}
	}

	@Override
	public void completeUser(long userId, long defaultLocationId, Instant completedAt) {
		jdbcTemplate.update(
			"""
			UPDATE users
			SET default_location_id = ?, signup_status = 'COMPLETED',
			    onboarding_completed_at = ?, updated_at = ?
			WHERE id = ? AND deleted_at IS NULL
			""",
			defaultLocationId,
			timestamp(completedAt),
			timestamp(completedAt),
			userId);
	}

	private Optional<UserRecord> findUser(String publicId, boolean forUpdate) {
		String lockClause = forUpdate ? " FOR UPDATE" : "";
		return jdbcTemplate.query(
			"""
			SELECT id, signup_status, default_location_id, onboarding_completed_at
			FROM users
			WHERE public_id = ? AND deleted_at IS NULL
			""" + lockClause,
			this::mapUser,
			publicId).stream().findFirst();
	}

	private UserRecord mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
		return new UserRecord(
			resultSet.getLong("id"),
			SignupStatus.valueOf(resultSet.getString("signup_status")),
			nullableLong(resultSet, "default_location_id"),
			instant(resultSet, "onboarding_completed_at"));
	}

	private LocationRecord mapLocation(ResultSet resultSet, int rowNumber) throws SQLException {
		return new LocationRecord(
			resultSet.getLong("id"),
			resultSet.getString("display_name"),
			ServiceRegionCode.valueOf(resultSet.getString("service_region_code")),
			resultSet.getTimestamp("deleted_at") == null);
	}

	private CandidateSetRecord mapCandidateSet(
		ResultSet resultSet,
		int rowNumber
	) throws SQLException {
		Long version = nullableLong(resultSet, "version");
		return new CandidateSetRecord(
			resultSet.getLong("id"),
			resultSet.getString("public_id"),
			version == null ? null : Math.toIntExact(version),
			CandidateSetStatus.valueOf(resultSet.getString("status")),
			instant(resultSet, "published_at"));
	}

	private SelectionRow mapSelection(ResultSet resultSet, int rowNumber) throws SQLException {
		return new SelectionRow(
			resultSet.getLong("candidate_set_id"),
			resultSet.getString("public_id"),
			Math.toIntExact(resultSet.getLong("version")),
			resultSet.getLong("place_id"));
	}

	private static Timestamp timestamp(Instant instant) {
		return Timestamp.from(instant);
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	private record SelectionRow(
		long candidateSetId,
		String candidateSetPublicId,
		int candidateSetVersion,
		long placeId
	) {
	}
}
