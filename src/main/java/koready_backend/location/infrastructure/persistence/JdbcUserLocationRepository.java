package koready_backend.location.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import koready_backend.location.application.port.UserLocationRepository;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.PlaceLanguage;

@Repository
public class JdbcUserLocationRepository implements UserLocationRepository {

	private static final String COMPLETE_LOCATION = """
		provider IS NOT NULL
		AND (road_address IS NOT NULL OR address IS NOT NULL)
		AND latitude IS NOT NULL
		AND longitude IS NOT NULL
		AND sido IS NOT NULL
		AND sigungu IS NOT NULL
		""";

	private static final String LOCATION_COLUMNS = """
		id, user_id, display_name, custom_label, provider, provider_place_id,
		road_address, address, postal_code, latitude, longitude, sido, sigungu, dong,
		service_region_code, created_at
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcUserLocationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<UserAccount> findActiveUser(String publicId) {
		return findUser(publicId, false);
	}

	@Override
	public Optional<UserAccount> findActiveUserForUpdate(String publicId) {
		return findUser(publicId, true);
	}

	private Optional<UserAccount> findUser(String publicId, boolean forUpdate) {
		String sql = """
			SELECT id, default_location_id, preferred_language
			FROM users
			WHERE public_id = ? AND deleted_at IS NULL
			""" + (forUpdate ? "FOR UPDATE" : "");
		return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
			long userId = resultSet.getLong("id");
			long defaultLocationId = resultSet.getLong("default_location_id");
			return new UserAccount(
				userId,
				resultSet.wasNull() ? null : defaultLocationId,
				PlaceLanguage.valueOf(resultSet.getString("preferred_language")));
		}, publicId).stream().findFirst();
	}

	@Override
	public List<UserLocationRecord> findAllCompleteActive(
		long userId,
		Long defaultLocationId,
		PlaceLanguage language
	) {
		String sql = localizedSelect(language) + " WHERE "
			+ "user_id = ? AND deleted_at IS NULL AND " + COMPLETE_LOCATION
			+ " ORDER BY CASE WHEN id = ? THEN 0 ELSE 1 END, created_at DESC, id DESC";
		return jdbcTemplate.query(connection -> {
			PreparedStatement statement = connection.prepareStatement(sql);
			statement.setLong(1, userId);
			if (defaultLocationId == null) {
				statement.setNull(2, Types.BIGINT);
			} else {
				statement.setLong(2, defaultLocationId);
			}
			return statement;
		}, this::mapLocation);
	}

	@Override
	public Optional<UserLocationRecord> findCompleteActive(
		long userId, long locationId, PlaceLanguage language
	) {
		String sql = localizedSelect(language) + " WHERE "
			+ "user_id = ? AND id = ? AND deleted_at IS NULL AND " + COMPLETE_LOCATION;
		return jdbcTemplate.query(sql, this::mapLocation, userId, locationId)
			.stream().findFirst();
	}

	@Override
	public Optional<UserLocationRecord> findNewestCompleteActiveExcluding(
		long userId,
		long excludedLocationId
	) {
		String sql = "SELECT " + LOCATION_COLUMNS + " FROM user_locations WHERE "
			+ "user_id = ? AND id <> ? AND deleted_at IS NULL AND " + COMPLETE_LOCATION
			+ " ORDER BY created_at DESC, id DESC LIMIT 1";
		return jdbcTemplate.query(sql, this::mapLocation, userId, excludedLocationId)
			.stream().findFirst();
	}

	@Override
	public UserLocationRecord create(
		long userId,
		NewLocation location,
		Instant createdAt
	) {
		var keyHolder = new GeneratedKeyHolder();
		Timestamp timestamp = Timestamp.from(createdAt);
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(
				"""
				INSERT INTO user_locations
				    (user_id, display_name, custom_label, provider, provider_place_id,
				     road_address, address, postal_code, latitude, longitude, sido, sigungu,
				     dong, service_region_code, created_at, updated_at, deleted_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setLong(1, userId);
			statement.setString(2, location.displayName());
			statement.setString(3, location.customLabel());
			statement.setString(4, location.provider());
			statement.setString(5, location.providerPlaceId());
			statement.setString(6, location.roadAddress());
			statement.setString(7, location.address());
			statement.setString(8, location.postalCode());
			statement.setDouble(9, location.latitude());
			statement.setDouble(10, location.longitude());
			statement.setString(11, location.sido());
			statement.setString(12, location.sigungu());
			statement.setString(13, location.dong());
			statement.setString(14, location.serviceRegionCode().name());
			statement.setTimestamp(15, timestamp);
			statement.setTimestamp(16, timestamp);
			return statement;
		}, keyHolder);

		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("User location key was not generated");
		}
		return findCompleteActive(userId, key.longValue(), PlaceLanguage.KO)
			.orElseThrow(() -> new IllegalStateException(
				"Created user location could not be loaded"));
	}

	@Override
	public void saveLocalization(
		long locationId,
		PlaceLanguage language,
		LocalizedLocation location,
		Instant updatedAt
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO user_location_localizations
			    (user_location_id, language, display_name, road_address, address,
			     created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE
			    display_name = VALUES(display_name),
			    road_address = VALUES(road_address),
			    address = VALUES(address),
			    updated_at = VALUES(updated_at)
			""",
			locationId, language.name(), location.displayName(), location.roadAddress(),
			location.address(), Timestamp.from(updatedAt), Timestamp.from(updatedAt));
	}

	@Override
	public boolean hasLocalization(long locationId, PlaceLanguage language) {
		Integer count = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*) FROM user_location_localizations
			WHERE user_location_id = ? AND language = ?
			""",
			Integer.class,
			locationId,
			language.name());
		return count != null && count > 0;
	}

	@Override
	public void updateDefaultLocation(
		long userId,
		Long locationId,
		Instant updatedAt
	) {
		jdbcTemplate.update(
			"""
			UPDATE users
			SET default_location_id = ?, updated_at = ?
			WHERE id = ? AND deleted_at IS NULL
			""",
			locationId,
			Timestamp.from(updatedAt),
			userId);
	}

	@Override
	public void softDelete(long userId, long locationId, Instant deletedAt) {
		Timestamp timestamp = Timestamp.from(deletedAt);
		jdbcTemplate.update(
			"""
			UPDATE user_locations
			SET deleted_at = ?, updated_at = ?
			WHERE user_id = ? AND id = ? AND deleted_at IS NULL
			""",
			timestamp,
			timestamp,
			userId,
			locationId);
	}

	private UserLocationRecord mapLocation(ResultSet resultSet, int rowNumber)
		throws SQLException {
		return new UserLocationRecord(
			resultSet.getLong("id"),
			resultSet.getLong("user_id"),
			resultSet.getString("display_name"),
			resultSet.getString("custom_label"),
			resultSet.getString("provider"),
			resultSet.getString("provider_place_id"),
			resultSet.getString("road_address"),
			resultSet.getString("address"),
			resultSet.getString("postal_code"),
			resultSet.getDouble("latitude"),
			resultSet.getDouble("longitude"),
			resultSet.getString("sido"),
			resultSet.getString("sigungu"),
			resultSet.getString("dong"),
			ServiceRegionCode.valueOf(resultSet.getString("service_region_code")),
			resultSet.getTimestamp("created_at").toInstant());
	}

	private static String localizedSelect(PlaceLanguage language) {
		return """
			SELECT location.id, location.user_id,
			       COALESCE(localized.display_name, location.display_name) AS display_name,
			       location.custom_label, location.provider, location.provider_place_id,
			       COALESCE(localized.road_address, location.road_address) AS road_address,
			       COALESCE(localized.address, location.address) AS address,
			       location.postal_code, location.latitude, location.longitude,
			       location.sido, location.sigungu, location.dong,
			       location.service_region_code, location.created_at
			FROM user_locations location
			LEFT JOIN user_location_localizations localized
			"""
			+ " ON localized.user_location_id = location.id AND localized.language = '"
			+ language.name() + "'";
	}
}
