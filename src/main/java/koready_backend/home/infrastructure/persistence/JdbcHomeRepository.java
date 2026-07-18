package koready_backend.home.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.home.application.port.HomeRepository;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;

@Repository
public class JdbcHomeRepository implements HomeRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcHomeRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<HomeUser> findByPublicId(String userPublicId) {
		List<HomeUser> users = jdbcTemplate.query(
			"""
			SELECT
			    user.id AS user_id,
			    user.public_id,
			    user.preferred_language,
			    location.id AS location_id,
			    location.display_name,
			    location.service_region_code
			FROM users user
			LEFT JOIN user_locations location
			  ON location.id = user.default_location_id
			 AND location.user_id = user.id
			 AND location.deleted_at IS NULL
			WHERE user.public_id = ? AND user.deleted_at IS NULL
			""",
			this::homeUser,
			userPublicId);
		return users.stream().findFirst();
	}

	private HomeUser homeUser(ResultSet rs, int rowNumber) throws SQLException {
		long locationId = rs.getLong("location_id");
		HomeLocation location = rs.wasNull()
			? null
			: new HomeLocation(
				locationId,
				rs.getString("display_name"),
				ServiceRegionCode.valueOf(rs.getString("service_region_code")));
		return new HomeUser(
			rs.getLong("user_id"),
			rs.getString("public_id"),
			PlaceLanguage.valueOf(rs.getString("preferred_language")),
			location);
	}
}
