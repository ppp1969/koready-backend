package koready_backend.place.infrastructure.persistence;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.place.application.port.SavedPlaceStatusPort;

@Repository
public class JdbcSavedPlaceStatusRepository implements SavedPlaceStatusPort {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public JdbcSavedPlaceStatusRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Set<Long> findSavedPlaceIds(String userPublicId, Collection<Long> placeIds) {
		if (userPublicId == null || userPublicId.isBlank()
			|| placeIds == null || placeIds.isEmpty()) {
			return Set.of();
		}
		var parameters = new MapSqlParameterSource()
			.addValue("userPublicId", userPublicId)
			.addValue("placeIds", placeIds.stream().distinct().toList());
		return jdbcTemplate.query(
				"""
				SELECT saved.place_id
				FROM user_saved_places saved
				JOIN users user_account ON user_account.id = saved.user_id
				WHERE user_account.public_id = :userPublicId
				  AND user_account.deleted_at IS NULL
				  AND saved.deleted_at IS NULL
				  AND saved.place_id IN (:placeIds)
				""",
				parameters,
				(resultSet, rowNumber) -> resultSet.getLong("place_id"))
			.stream()
			.collect(Collectors.toUnmodifiableSet());
	}
}
