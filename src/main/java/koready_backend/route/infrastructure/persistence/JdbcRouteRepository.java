package koready_backend.route.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.route.application.port.RouteRepository;
import koready_backend.route.domain.RoutePlan;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcRouteRepository implements RouteRepository {

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	public JdbcRouteRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public Optional<RouteContext> findContext(
		String userSubject,
		long locationId,
		long placeId
	) {
		return jdbcTemplate.query(
			"""
			SELECT
			    user.id AS user_id,
			    user.preferred_language,
			    location.display_name AS origin_name,
			    COALESCE(location.road_address, location.address) AS origin_address,
			    location.latitude AS origin_latitude,
			    location.longitude AS origin_longitude,
			    COALESCE(destination_text.title, korean.title) AS destination_name,
			    COALESCE(destination_text.address_text, korean.address_text,
			             place.road_address, place.address) AS destination_address,
			    place.latitude AS destination_latitude,
			    place.longitude AS destination_longitude
			FROM users user
			JOIN user_locations location
			  ON location.user_id = user.id
			 AND location.id = ?
			 AND location.deleted_at IS NULL
			JOIN places place
			  ON place.id = ?
			 AND place.active = TRUE
			 AND place.latitude IS NOT NULL
			 AND place.longitude IS NOT NULL
			LEFT JOIN place_localizations destination_text
			  ON destination_text.place_id = place.id
			 AND destination_text.language = user.preferred_language
			LEFT JOIN place_localizations korean
			  ON korean.place_id = place.id AND korean.language = 'KO'
			WHERE user.public_id = ?
			  AND user.deleted_at IS NULL
			  AND location.latitude IS NOT NULL
			  AND location.longitude IS NOT NULL
			  AND COALESCE(destination_text.title, korean.title) IS NOT NULL
			""",
			(resultSet, rowNumber) -> new RouteContext(
				resultSet.getLong("user_id"),
				resultSet.getString("preferred_language"),
				resultSet.getString("origin_name"),
				resultSet.getString("origin_address"),
				resultSet.getDouble("origin_latitude"),
				resultSet.getDouble("origin_longitude"),
				resultSet.getString("destination_name"),
				resultSet.getString("destination_address"),
				resultSet.getDouble("destination_latitude"),
				resultSet.getDouble("destination_longitude")),
			locationId, placeId, userSubject).stream().findFirst();
	}

	@Override
	public void save(long userId, RoutePlan route) {
		jdbcTemplate.update(
			"""
			INSERT INTO route_caches
			    (public_id, user_id, destination_place_id, normalized_route_json,
			     fetched_at, expires_at, created_at)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			""",
			route.routeId(), userId, route.destinationPlaceId(), json(route),
			Timestamp.from(route.fetchedAt()), Timestamp.from(route.expiresAt()),
			Timestamp.from(route.fetchedAt()));
	}

	@Override
	public Optional<RoutePlan> findOwned(String routeId, String userSubject) {
		return jdbcTemplate.query(
			"""
			SELECT cache.normalized_route_json
			FROM route_caches cache
			JOIN users user ON user.id = cache.user_id AND user.deleted_at IS NULL
			WHERE cache.public_id = ? AND user.public_id = ?
			""",
			(resultSet, rowNumber) -> parse(resultSet.getString("normalized_route_json")),
			routeId, userSubject).stream().findFirst();
	}

	@Override
	public void deleteExpired(Instant now) {
		jdbcTemplate.update("DELETE FROM route_caches WHERE expires_at <= ?", Timestamp.from(now));
	}

	private String json(RoutePlan route) {
		try {
			return jsonMapper.writeValueAsString(route);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Normalized route could not be stored", exception);
		}
	}

	private RoutePlan parse(String json) {
		try {
			return jsonMapper.readValue(json, RoutePlan.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Normalized route could not be read", exception);
		}
	}
}
