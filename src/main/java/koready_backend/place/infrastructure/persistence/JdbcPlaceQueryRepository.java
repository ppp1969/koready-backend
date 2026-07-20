package koready_backend.place.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.place.application.port.PlaceQueryRepository;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.PlaceSort;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@Repository
public class JdbcPlaceQueryRepository implements PlaceQueryRepository {

	private static final String DEADLINE_EXPRESSION = """
		(SELECT MIN(event.end_date)
		 FROM place_event_occurrences event
		 WHERE event.place_id = p.id
		   AND event.date_validation_status = 'VALID'
		   AND event.visible_from <= :today
		   AND event.end_date >= :today)
		""";

	private static final String PLACE_SELECT = """
		SELECT
		    p.id AS place_id,
		    COALESCE(requested.title, korean.title) AS title,
		    p.service_region_code,
		    CASE WHEN :language = 'EN' THEN region.name_en ELSE region.name_ko END
		        AS service_region_name,
		    COALESCE(
		        requested.address_text,
		        korean.address_text,
		        p.road_address,
		        p.address,
		        ''
		    ) AS address_summary,
		    NULLIF(TRIM(p.first_image_url), '') AS image_url,
		    (SELECT style.travel_style
		     FROM place_style_mappings style
		     WHERE style.place_id = p.id
		     ORDER BY style.confidence DESC, style.travel_style ASC
		     LIMIT 1) AS travel_style,
		    COALESCE(requested.overview, korean.overview) AS overview,
		    p.data_quality_score,
		    %s AS deadline_sort_date
		FROM places p
		JOIN service_regions region ON region.code = p.service_region_code
		LEFT JOIN place_localizations requested
		    ON requested.place_id = p.id AND requested.language = :language
		LEFT JOIN place_localizations korean
		    ON korean.place_id = p.id AND korean.language = 'KO'
		WHERE p.active = TRUE
		  AND p.show_flag = TRUE
		  AND COALESCE(requested.id, korean.id) IS NOT NULL
		%s
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

	private static final String PLACE_DETAIL = """
		SELECT
		    p.id AS place_id,
		    COALESCE(requested.title, korean.title) AS title,
		    p.service_region_code,
		    CASE WHEN :language = 'EN' THEN region.name_en ELSE region.name_ko END
		        AS service_region_name,
		    NULLIF(TRIM(COALESCE(
		        requested.address_text,
		        korean.address_text,
		        p.road_address,
		        p.address,
		        ''
		    )), '') AS address,
		    p.latitude,
		    p.longitude,
		    NULLIF(TRIM(p.first_image_url), '') AS image_url,
		    COALESCE(requested.overview, korean.overview) AS overview,
		    COALESCE(requested.translation_source, korean.translation_source)
		        AS translation_source
		FROM places p
		JOIN service_regions region ON region.code = p.service_region_code
		LEFT JOIN place_localizations requested
		    ON requested.place_id = p.id AND requested.language = :language
		LEFT JOIN place_localizations korean
		    ON korean.place_id = p.id AND korean.language = 'KO'
		WHERE p.id = :placeId
		  AND p.active = TRUE
		  AND p.show_flag = TRUE
		  AND COALESCE(requested.id, korean.id) IS NOT NULL
		""";

	private static final String PLACE_IMAGES = """
		SELECT
		    image.image_url,
		    COALESCE(NULLIF(TRIM(image.source_image_name), ''), localized.title) AS alt_text
		FROM place_images image
		JOIN place_localizations localized
		    ON localized.place_id = image.place_id AND localized.language = 'KO'
		WHERE image.place_id = :placeId
		ORDER BY image.source_priority DESC, image.source_order ASC, image.id ASC
		LIMIT 3
		""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public JdbcPlaceQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<PlaceRow> findByRegion(PlaceListCriteria criteria) {
		MapSqlParameterSource parameters = commonParameters(
			criteria.language(), criteria.today(), criteria.limit());
		parameters.addValue("serviceRegionCode", criteria.serviceRegionCode().name());

		StringBuilder condition = new StringBuilder("\n  AND p.service_region_code = :serviceRegionCode");
		if (!criteria.travelStyles().isEmpty()) {
			condition.append("""

				  AND EXISTS (
				      SELECT 1
				      FROM place_style_mappings filter_style
				      WHERE filter_style.place_id = p.id
				        AND filter_style.travel_style IN (:travelStyles)
				  )
				""");
			parameters.addValue(
				"travelStyles",
				criteria.travelStyles().stream().map(Enum::name).toList());
		}

		return queryPlaces(condition.toString(), criteria.sort(), criteria.cursor(), parameters);
	}

	@Override
	public List<PlaceRow> search(PlaceSearchCriteria criteria) {
		MapSqlParameterSource parameters = commonParameters(
			criteria.language(), criteria.today(), criteria.limit());
		parameters.addValue("query", "%" + escapeLike(criteria.query()) + "%");
		String condition = """

			  AND (
			      COALESCE(requested.title, korean.title) LIKE :query ESCAPE '!'
			      OR COALESCE(requested.address_text, korean.address_text, p.road_address, p.address, '')
			          LIKE :query ESCAPE '!'
			  )
			""";

		return queryPlaces(condition, PlaceSort.RECOMMENDED, criteria.cursor(), parameters);
	}

	@Override
	public Optional<PlaceDetailRow> findDetail(long placeId, PlaceLanguage language) {
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("placeId", placeId)
			.addValue("language", language.name());
		return jdbcTemplate.query(PLACE_DETAIL, parameters, this::mapDetail)
			.stream()
			.findFirst();
	}

	@Override
	public List<PlaceImageRow> findImages(long placeId) {
		return jdbcTemplate.query(
			PLACE_IMAGES,
			new MapSqlParameterSource("placeId", placeId),
			(resultSet, rowNumber) -> new PlaceImageRow(
				resultSet.getString("image_url"),
				resultSet.getString("alt_text")));
	}

	private List<PlaceRow> queryPlaces(
		String condition,
		PlaceSort sort,
		PlaceCursor cursor,
		MapSqlParameterSource parameters
	) {
		String baseQuery = PLACE_SELECT.formatted(DEADLINE_EXPRESSION, condition);
		StringBuilder sql = new StringBuilder("SELECT * FROM (\n")
			.append(baseQuery)
			.append("\n) candidate\nWHERE 1 = 1\n");

		if (cursor != null) {
			parameters.addValue("cursorPlaceId", cursor.placeId());
			if (sort == PlaceSort.RECOMMENDED) {
				parameters.addValue("cursorScore", cursor.qualityScore());
				sql.append("""
					AND (
					    candidate.data_quality_score < :cursorScore
					    OR (
					        candidate.data_quality_score = :cursorScore
					        AND candidate.place_id < :cursorPlaceId
					    )
					)
					""");
			} else if (cursor.deadlineSortDate() == null) {
				sql.append("""
					AND candidate.deadline_sort_date IS NULL
					AND candidate.place_id < :cursorPlaceId
					""");
			} else {
				parameters.addValue("cursorDeadline", cursor.deadlineSortDate());
				sql.append("""
					AND (
					    candidate.deadline_sort_date > :cursorDeadline
					    OR candidate.deadline_sort_date IS NULL
					    OR (
					        candidate.deadline_sort_date = :cursorDeadline
					        AND candidate.place_id < :cursorPlaceId
					    )
					)
					""");
			}
		}

		if (sort == PlaceSort.RECOMMENDED) {
			sql.append("ORDER BY candidate.data_quality_score DESC, candidate.place_id DESC\n");
		} else {
			sql.append("""
				ORDER BY
				    candidate.deadline_sort_date IS NULL,
				    candidate.deadline_sort_date ASC,
				    candidate.place_id DESC
				""");
		}
		sql.append("LIMIT :limit");

		List<PlaceRow> rows = jdbcTemplate.query(sql.toString(), parameters, this::mapPlace);
		return attachOccurrences(rows, (LocalDate) parameters.getValue("today"));
	}

	private List<PlaceRow> attachOccurrences(List<PlaceRow> rows, LocalDate today) {
		if (rows.isEmpty()) {
			return rows;
		}
		List<Long> placeIds = rows.stream().map(PlaceRow::placeId).toList();
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("placeIds", placeIds)
			.addValue("today", today);
		Map<Long, FestivalOccurrence> occurrences = new HashMap<>();
		jdbcTemplate.query(OCCURRENCES_FOR_PLACES, parameters, resultSet -> {
			long placeId = resultSet.getLong("place_id");
			occurrences.put(placeId, new FestivalOccurrence(
				resultSet.getLong("occurrence_id"),
				resultSet.getInt("event_year"),
				resultSet.getObject("start_date", LocalDate.class),
				resultSet.getObject("end_date", LocalDate.class)));
		});

		return rows.stream()
			.map(row -> new PlaceRow(
				row.placeId(),
				row.title(),
				row.serviceRegionCode(),
				row.serviceRegionName(),
				row.addressSummary(),
				row.imageUrl(),
				row.travelStyle(),
				row.overview(),
				row.qualityScore(),
				row.deadlineSortDate(),
				occurrences.get(row.placeId())))
			.toList();
	}

	private PlaceRow mapPlace(ResultSet resultSet, int rowNumber) throws SQLException {
		String travelStyle = resultSet.getString("travel_style");
		return new PlaceRow(
			resultSet.getLong("place_id"),
			resultSet.getString("title"),
			ServiceRegionCode.valueOf(resultSet.getString("service_region_code")),
			resultSet.getString("service_region_name"),
			resultSet.getString("address_summary"),
			resultSet.getString("image_url"),
			travelStyle == null ? null : TravelStyle.valueOf(travelStyle),
			resultSet.getString("overview"),
			resultSet.getBigDecimal("data_quality_score"),
			resultSet.getObject("deadline_sort_date", LocalDate.class),
			null);
	}

	private PlaceDetailRow mapDetail(ResultSet resultSet, int rowNumber) throws SQLException {
		return new PlaceDetailRow(
			resultSet.getLong("place_id"),
			resultSet.getString("title"),
			ServiceRegionCode.valueOf(resultSet.getString("service_region_code")),
			resultSet.getString("service_region_name"),
			resultSet.getString("address"),
			resultSet.getBigDecimal("latitude"),
			resultSet.getBigDecimal("longitude"),
			resultSet.getString("image_url"),
			resultSet.getString("overview"),
			resultSet.getString("translation_source"));
	}

	private static MapSqlParameterSource commonParameters(
		PlaceLanguage language,
		LocalDate today,
		int limit
	) {
		return new MapSqlParameterSource()
			.addValue("language", language.name())
			.addValue("today", today)
			.addValue("limit", limit);
	}

	static String escapeLike(String value) {
		return value
			.replace("!", "!!")
			.replace("%", "!%")
			.replace("_", "!_");
	}
}
