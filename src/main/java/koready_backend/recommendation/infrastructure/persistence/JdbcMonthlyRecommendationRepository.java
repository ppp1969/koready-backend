package koready_backend.recommendation.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;

import koready_backend.editorial.application.EditorialProperties;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository;
import koready_backend.recommendation.domain.RecommendationSort;

@Repository
public class JdbcMonthlyRecommendationRepository implements MonthlyRecommendationRepository {

	private static final String STATUS_RANK = """
		CASE
		    WHEN event.end_date < :today THEN 1
		    ELSE 0
		END
		""";

	private static final String PRIMARY_STYLE = """
		(SELECT style.travel_style
		 FROM place_style_mappings style
		 WHERE style.place_id = p.id
		 ORDER BY style.is_primary DESC, style.confidence DESC, style.travel_style ASC
		 LIMIT 1)
		""";

	private static final String SELECT_COLUMNS = """
		SELECT
		    COALESCE(event.id, -p.id) AS occurrence_id,
		    p.id AS place_id,
		    event.event_year,
		    event.start_date,
		    event.end_date,
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
		    COALESCE(
		        (SELECT image.image_url FROM place_images image
		         WHERE image.place_id = p.id
		         ORDER BY image.source_priority DESC, image.source_order ASC, image.id ASC
		         LIMIT 1),
		        NULLIF(TRIM(p.first_image_url), '')
		    ) AS image_url,
		    (SELECT attribute.value_text
		     FROM place_detail_attributes attribute
		     WHERE attribute.place_id = p.id
		       AND attribute.field_code IN (
		           'usetime', 'opentimefood', 'usetimeculture',
		           'usetimeleports', 'checkintime'
		       )
		       AND NULLIF(TRIM(attribute.value_text), '') IS NOT NULL
		     ORDER BY
		         FIELD(attribute.field_code, 'usetime', 'opentimefood',
		               'usetimeculture', 'usetimeleports', 'checkintime'),
		         attribute.item_sequence ASC,
		         attribute.id ASC
		     LIMIT 1) AS operating_hours,
		    %s AS travel_style,
		    requested.overview AS overview,
		    COALESCE(hearts.heart_count, 0) AS heart_count,
		    p.data_quality_score,
		    %s AS recommendation_status_rank
		""".formatted(PRIMARY_STYLE, STATUS_RANK);

	private static final String BASE_FROM = """
		FROM places p
		LEFT JOIN place_event_occurrences event
		    ON event.place_id = p.id
		   AND event.date_validation_status = 'VALID'
		   AND event.start_date <= :queryEnd
		   AND event.end_date >= :queryStart
		   AND event.visible_from <= :today
		   AND event.start_date <= DATE_ADD(:today, INTERVAL 6 MONTH)
		   AND EXISTS (
		       SELECT 1 FROM place_style_mappings festival_style
		       WHERE festival_style.place_id = p.id
		         AND festival_style.travel_style = 'LOCAL_FESTIVAL'
		   )
		JOIN service_regions region ON region.code = p.service_region_code
		LEFT JOIN (
		    SELECT saved.place_id, COUNT(*) AS heart_count
		    FROM user_saved_places saved
		    WHERE saved.deleted_at IS NULL
		    GROUP BY saved.place_id
		) hearts ON hearts.place_id = p.id
		LEFT JOIN place_localizations requested
		    ON requested.place_id = p.id AND requested.language = :language
		LEFT JOIN place_localizations korean
		    ON korean.place_id = p.id AND korean.language = 'KO'
		WHERE p.active = TRUE
		  AND p.show_flag = TRUE
		  AND EXISTS (SELECT 1 FROM place_style_mappings style WHERE style.place_id = p.id)
		  AND (
		      NULLIF(TRIM(p.first_image_url), '') IS NOT NULL
		      OR EXISTS (SELECT 1 FROM place_images image WHERE image.place_id = p.id)
		  )
		  AND COALESCE(requested.id, korean.id) IS NOT NULL
		  AND EXISTS (
		      SELECT 1
		      FROM place_localizations publication_en
		      WHERE publication_en.place_id = p.id
		        AND publication_en.language = 'EN'
		        AND publication_en.translation_source IN ('KTO_EN', 'MANUAL_EDITED')
		        AND NULLIF(TRIM(publication_en.title), '') IS NOT NULL
		  )
		  AND (
		      event.id IS NOT NULL
		      OR (
		          :includeEvergreen = TRUE
		          AND NOT EXISTS (
		              SELECT 1 FROM place_style_mappings festival_style
		              WHERE festival_style.place_id = p.id
		                AND festival_style.travel_style = 'LOCAL_FESTIVAL'
		          )
		      )
		  )
		""";

	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final EditorialProperties editorialProperties;

	public JdbcMonthlyRecommendationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this(jdbcTemplate, new EditorialProperties(null, false));
	}

	@Autowired
	public JdbcMonthlyRecommendationRepository(
		NamedParameterJdbcTemplate jdbcTemplate,
		EditorialProperties editorialProperties
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.editorialProperties = editorialProperties;
	}

	@Override
	public List<MonthlyRecommendationRow> findPage(MonthlyRecommendationPageQuery query) {
		MapSqlParameterSource parameters = parameters(query.filter())
			.addValue("limit", query.limit());
		if (editorialProperties.publicationFilterEnabled()) {
			parameters.addValue(
				"editorialPromptVersion", editorialProperties.promptVersion());
		}
		String filterConditions = filterConditions(query.filter(), parameters);
		StringBuilder sql = new StringBuilder("SELECT * FROM (\n")
			.append(SELECT_COLUMNS)
			.append(BASE_FROM)
			.append(editorialReadyCondition())
			.append(filterConditions)
			.append("\n) candidate\nWHERE 1 = 1\n");

		if (query.cursor() != null) {
			parameters.addValue("cursorOccurrenceId", query.cursor().occurrenceId());
			if (query.filter().sort() == RecommendationSort.RECOMMENDED) {
				parameters
					.addValue("cursorStatusRank", query.cursor().statusRank())
					.addValue("cursorHeartCount", query.cursor().heartCount())
					.addValue("cursorScore", query.cursor().qualityScore());
				sql.append("""
					AND (
					    candidate.recommendation_status_rank > :cursorStatusRank
					    OR (
					        candidate.recommendation_status_rank = :cursorStatusRank
					        AND candidate.heart_count < :cursorHeartCount
					    )
					    OR (
					        candidate.recommendation_status_rank = :cursorStatusRank
					        AND candidate.heart_count = :cursorHeartCount
					        AND candidate.data_quality_score < :cursorScore
					    )
					    OR (
					        candidate.recommendation_status_rank = :cursorStatusRank
					        AND candidate.heart_count = :cursorHeartCount
					        AND candidate.data_quality_score = :cursorScore
					        AND candidate.occurrence_id < :cursorOccurrenceId
					    )
					)
					""");
			} else {
				parameters
					.addValue("cursorStatusRank", query.cursor().statusRank())
					.addValue("cursorEndDate", query.cursor().endDate());
				sql.append("""
					AND (
					    candidate.recommendation_status_rank > :cursorStatusRank
					    OR (
					        candidate.recommendation_status_rank = :cursorStatusRank
					        AND candidate.end_date > :cursorEndDate
					    )
					    OR (
					        candidate.recommendation_status_rank = :cursorStatusRank
					        AND
					        candidate.end_date = :cursorEndDate
					        AND candidate.occurrence_id < :cursorOccurrenceId
					    )
					)
					""");
			}
		}

		if (query.filter().sort() == RecommendationSort.RECOMMENDED) {
			sql.append("""
				ORDER BY
				    candidate.recommendation_status_rank ASC,
				    candidate.heart_count DESC,
				    candidate.data_quality_score DESC,
				    candidate.occurrence_id DESC
				""");
		} else {
			sql.append("""
				ORDER BY candidate.recommendation_status_rank ASC,
				         candidate.end_date ASC,
				         candidate.occurrence_id DESC
				""");
		}
		sql.append("LIMIT :limit");

		return jdbcTemplate.query(sql.toString(), parameters, this::mapRow);
	}

	private String editorialReadyCondition() {
		if (!editorialProperties.publicationFilterEnabled()) {
			return "";
		}
		return """

			  AND EXISTS (
			      SELECT 1 FROM place_editorial_contents editorial
			      WHERE editorial.place_id = p.id
			        AND editorial.status = 'READY'
			        AND editorial.prompt_version = :editorialPromptVersion
			  )
			""";
	}

	@Override
	public long count(MonthlyRecommendationFilter filter) {
		MapSqlParameterSource parameters = parameters(filter);
		String sql = "SELECT COUNT(*)\n"
			+ BASE_FROM
			+ filterConditions(filter, parameters);
		Long count = jdbcTemplate.queryForObject(sql, parameters, Long.class);
		return count == null ? 0L : count;
	}

	private static MapSqlParameterSource parameters(MonthlyRecommendationFilter filter) {
		return new MapSqlParameterSource()
			.addValue("queryStart", filter.startDate())
			.addValue("queryEnd", filter.endDate())
			.addValue("today", filter.today())
			.addValue("language", filter.language().name())
			.addValue("includeEvergreen", filter.includeEvergreen());
	}

	private static String filterConditions(
		MonthlyRecommendationFilter filter,
		MapSqlParameterSource parameters
	) {
		StringBuilder conditions = new StringBuilder();
		if (filter.serviceRegionCode() != null) {
			conditions.append("\n  AND p.service_region_code = :serviceRegionCode");
			parameters.addValue("serviceRegionCode", filter.serviceRegionCode().name());
		}
		if (!filter.travelStyles().isEmpty()) {
			conditions.append("""

				  AND EXISTS (
				      SELECT 1
				      FROM place_style_mappings filter_style
				      WHERE filter_style.place_id = p.id
				        AND filter_style.travel_style IN (:travelStyles)
				  )
				""");
			parameters.addValue(
				"travelStyles",
				filter.travelStyles().stream().map(Enum::name).toList());
		}
		return conditions.toString();
	}

	private MonthlyRecommendationRow mapRow(ResultSet resultSet, int rowNumber)
		throws SQLException {
		String travelStyle = resultSet.getString("travel_style");
		return new MonthlyRecommendationRow(
			resultSet.getLong("occurrence_id"),
			resultSet.getLong("place_id"),
			resultSet.getInt("event_year"),
			resultSet.getObject("start_date", java.time.LocalDate.class),
			resultSet.getObject("end_date", java.time.LocalDate.class),
			resultSet.getString("title"),
			ServiceRegionCode.valueOf(resultSet.getString("service_region_code")),
			resultSet.getString("service_region_name"),
			resultSet.getString("address_summary"),
			resultSet.getString("image_url"),
			resultSet.getString("operating_hours"),
			travelStyle == null ? null : TravelStyle.valueOf(travelStyle),
			resultSet.getString("overview"),
			resultSet.getLong("heart_count"),
			resultSet.getBigDecimal("data_quality_score"),
			resultSet.getInt("recommendation_status_rank"));
	}
}
