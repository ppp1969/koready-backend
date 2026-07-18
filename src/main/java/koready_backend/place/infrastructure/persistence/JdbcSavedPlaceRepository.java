package koready_backend.place.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.place.application.port.PlaceQueryRepository.FestivalOccurrence;
import koready_backend.place.application.port.SavedPlaceRepository;
import koready_backend.place.domain.SavedPlaceSource;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@Repository
public class JdbcSavedPlaceRepository implements SavedPlaceRepository {

	private static final String SAVED_PLACE_SELECT = """
		SELECT
		    saved.id AS saved_place_id,
		    saved.saved_at,
		    place.id AS place_id,
		    COALESCE(requested.title, korean.title) AS title,
		    place.service_region_code,
		    CASE WHEN :language = 'EN' THEN region.name_en ELSE region.name_ko END
		        AS service_region_name,
		    COALESCE(
		        requested.address_text,
		        korean.address_text,
		        place.road_address,
		        place.address,
		        ''
		    ) AS address_summary,
		    NULLIF(TRIM(place.first_image_url), '') AS image_url,
		    (SELECT style.travel_style
		     FROM place_style_mappings style
		     WHERE style.place_id = place.id
		     ORDER BY style.confidence DESC, style.travel_style ASC
		     LIMIT 1) AS travel_style,
		    COALESCE(requested.overview, korean.overview) AS overview,
		    place.data_quality_score
		FROM user_saved_places saved
		JOIN places place ON place.id = saved.place_id
		JOIN service_regions region ON region.code = place.service_region_code
		LEFT JOIN place_localizations requested
		    ON requested.place_id = place.id AND requested.language = :language
		LEFT JOIN place_localizations korean
		    ON korean.place_id = place.id AND korean.language = 'KO'
		WHERE saved.user_id = :userId
		  AND saved.deleted_at IS NULL
		  AND place.active = TRUE
		  AND place.show_flag = TRUE
		  AND COALESCE(requested.id, korean.id) IS NOT NULL
		""";

	private static final String OCCURRENCES_FOR_PLACES = """
		SELECT occurrence_id, place_id, event_year, start_date, end_date
		FROM (
		    SELECT
		        event.id AS occurrence_id,
		        event.place_id,
		        event.event_year,
		        event.start_date,
		        event.end_date,
		        ROW_NUMBER() OVER (
		            PARTITION BY event.place_id
		            ORDER BY
		                CASE WHEN event.start_date <= :today THEN 0 ELSE 1 END,
		                event.end_date ASC,
		                event.id ASC
		        ) AS occurrence_rank
		    FROM place_event_occurrences event
		    WHERE event.place_id IN (:placeIds)
		      AND event.date_validation_status = 'VALID'
		      AND event.visible_from <= :today
		      AND event.end_date >= :today
		) ranked
		WHERE occurrence_rank = 1
		""";

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;

	public JdbcSavedPlaceRepository(
		JdbcTemplate jdbcTemplate,
		NamedParameterJdbcTemplate namedJdbcTemplate
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.namedJdbcTemplate = namedJdbcTemplate;
	}

	@Override
	public Optional<Long> findActiveUserId(String publicId) {
		return jdbcTemplate.query(
			"SELECT id FROM users WHERE public_id = ? AND deleted_at IS NULL",
			(resultSet, rowNumber) -> resultSet.getLong("id"),
			publicId).stream().findFirst();
	}

	@Override
	public boolean existsVisiblePlace(long placeId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"""
			SELECT EXISTS(
			    SELECT 1
			    FROM places
			    WHERE id = ? AND active = TRUE AND show_flag = TRUE
			)
			""",
			Boolean.class,
			placeId);
		return Boolean.TRUE.equals(exists);
	}

	@Override
	public SavedPlaceRecord save(
		long userId,
		long placeId,
		SavedPlaceSource source,
		Instant savedAt
	) {
		Timestamp timestamp = Timestamp.from(savedAt);
		jdbcTemplate.update(
			"""
			INSERT INTO user_saved_places
			    (user_id, place_id, source, saved_at, updated_at, deleted_at)
			VALUES (?, ?, ?, ?, ?, NULL)
			ON DUPLICATE KEY UPDATE
			    source = IF(
			        user_saved_places.deleted_at IS NULL,
			        user_saved_places.source,
			        VALUES(source)
			    ),
			    saved_at = IF(
			        user_saved_places.deleted_at IS NULL,
			        user_saved_places.saved_at,
			        VALUES(saved_at)
			    ),
			    updated_at = IF(
			        user_saved_places.deleted_at IS NULL,
			        user_saved_places.updated_at,
			        VALUES(updated_at)
			    ),
			    deleted_at = NULL
			""",
			userId,
			placeId,
			source.name(),
			timestamp,
			timestamp);

		return jdbcTemplate.queryForObject(
			"""
			SELECT place_id, saved_at
			FROM user_saved_places
			WHERE user_id = ? AND place_id = ? AND deleted_at IS NULL
			""",
			(resultSet, rowNumber) -> new SavedPlaceRecord(
				resultSet.getLong("place_id"),
				resultSet.getTimestamp("saved_at").toInstant()),
			userId,
			placeId);
	}

	@Override
	public void unsave(long userId, long placeId, Instant deletedAt) {
		Timestamp timestamp = Timestamp.from(deletedAt);
		jdbcTemplate.update(
			"""
			UPDATE user_saved_places
			SET deleted_at = ?, updated_at = ?
			WHERE user_id = ? AND place_id = ? AND deleted_at IS NULL
			""",
			timestamp,
			timestamp,
			userId,
			placeId);
	}

	@Override
	public List<SavedPlaceRow> findAll(SavedPlaceCriteria criteria) {
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("userId", criteria.userId())
			.addValue("language", criteria.language().name())
			.addValue("limit", criteria.limit());
		StringBuilder sql = new StringBuilder(SAVED_PLACE_SELECT);
		if (criteria.cursor() != null) {
			parameters
				.addValue("cursorSavedAt", Timestamp.from(criteria.cursor().savedAt()))
				.addValue("cursorSavedPlaceId", criteria.cursor().savedPlaceId());
			sql.append("""
				  AND (
				      saved.saved_at < :cursorSavedAt
				      OR (
				          saved.saved_at = :cursorSavedAt
				          AND saved.id < :cursorSavedPlaceId
				      )
				  )
				""");
		}
		sql.append("ORDER BY saved.saved_at DESC, saved.id DESC\nLIMIT :limit");
		List<SavedPlaceRow> rows = namedJdbcTemplate.query(
			sql.toString(), parameters, this::mapSavedPlace);
		return attachOccurrences(rows, criteria.today());
	}

	private List<SavedPlaceRow> attachOccurrences(
		List<SavedPlaceRow> rows,
		LocalDate today
	) {
		if (rows.isEmpty()) {
			return rows;
		}
		List<Long> placeIds = rows.stream().map(SavedPlaceRow::placeId).toList();
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("placeIds", placeIds)
			.addValue("today", today);
		Map<Long, FestivalOccurrence> occurrences = new HashMap<>();
		namedJdbcTemplate.query(OCCURRENCES_FOR_PLACES, parameters, resultSet -> {
			long placeId = resultSet.getLong("place_id");
			occurrences.put(placeId, new FestivalOccurrence(
				resultSet.getLong("occurrence_id"),
				resultSet.getInt("event_year"),
				resultSet.getObject("start_date", LocalDate.class),
				resultSet.getObject("end_date", LocalDate.class)));
		});

		return rows.stream().map(row -> new SavedPlaceRow(
			row.savedPlaceId(),
			row.placeId(),
			row.savedAt(),
			row.title(),
			row.serviceRegionCode(),
			row.serviceRegionName(),
			row.addressSummary(),
			row.imageUrl(),
			row.travelStyle(),
			row.overview(),
			row.qualityScore(),
			occurrences.get(row.placeId()))).toList();
	}

	private SavedPlaceRow mapSavedPlace(ResultSet resultSet, int rowNumber)
		throws SQLException {
		String travelStyle = resultSet.getString("travel_style");
		return new SavedPlaceRow(
			resultSet.getLong("saved_place_id"),
			resultSet.getLong("place_id"),
			resultSet.getTimestamp("saved_at").toInstant(),
			resultSet.getString("title"),
			ServiceRegionCode.valueOf(resultSet.getString("service_region_code")),
			resultSet.getString("service_region_name"),
			resultSet.getString("address_summary"),
			resultSet.getString("image_url"),
			travelStyle == null ? null : TravelStyle.valueOf(travelStyle),
			resultSet.getString("overview"),
			resultSet.getBigDecimal("data_quality_score"),
			null);
	}
}
