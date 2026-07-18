package koready_backend.recommendation.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository;
import koready_backend.recommendation.domain.RecommendationSort;

@Repository
public class JdbcMonthlyRecommendationRepository implements MonthlyRecommendationRepository {

	private static final String STATUS_RANK = """
		CASE
		    WHEN :today BETWEEN event.start_date AND event.end_date THEN 0
		    WHEN :today < event.start_date THEN 1
		    ELSE 2
		END
		""";

	private static final String PRIMARY_STYLE = """
		(SELECT style.travel_style
		 FROM place_style_mappings style
		 WHERE style.place_id = p.id
		 ORDER BY style.confidence DESC, style.travel_style ASC
		 LIMIT 1)
		""";

	private static final String SELECT_COLUMNS = """
		SELECT
		    event.id AS occurrence_id,
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
		    NULLIF(TRIM(p.first_image_url), '') AS image_url,
		    %s AS travel_style,
		    COALESCE(requested.overview, korean.overview) AS overview,
		    p.data_quality_score,
		    %s AS recommendation_status_rank
		""".formatted(PRIMARY_STYLE, STATUS_RANK);

	private static final String BASE_FROM = """
		FROM place_event_occurrences event
		JOIN places p ON p.id = event.place_id
		JOIN service_regions region ON region.code = p.service_region_code
		LEFT JOIN place_localizations requested
		    ON requested.place_id = p.id AND requested.language = :language
		LEFT JOIN place_localizations korean
		    ON korean.place_id = p.id AND korean.language = 'KO'
		WHERE p.active = TRUE
		  AND p.show_flag = TRUE
		  AND COALESCE(requested.id, korean.id) IS NOT NULL
		  AND event.date_validation_status = 'VALID'
		  AND event.start_date <= :queryEnd
		  AND event.end_date >= :queryStart
		  AND event.visible_from <= :today
		  AND event.start_date <= DATE_ADD(:today, INTERVAL 6 MONTH)
		""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public JdbcMonthlyRecommendationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<MonthlyRecommendationRow> findPage(MonthlyRecommendationPageQuery query) {
		MapSqlParameterSource parameters = parameters(query.filter())
			.addValue("limit", query.limit());
		String filterConditions = filterConditions(query.filter(), parameters);
		StringBuilder sql = new StringBuilder("SELECT * FROM (\n")
			.append(SELECT_COLUMNS)
			.append(BASE_FROM)
			.append(filterConditions)
			.append("\n) candidate\nWHERE 1 = 1\n");

		if (query.cursor() != null) {
			parameters.addValue("cursorOccurrenceId", query.cursor().occurrenceId());
			if (query.filter().sort() == RecommendationSort.RECOMMENDED) {
				parameters
					.addValue("cursorStatusRank", query.cursor().statusRank())
					.addValue("cursorScore", query.cursor().qualityScore());
				sql.append("""
					AND (
					    candidate.recommendation_status_rank > :cursorStatusRank
					    OR (
					        candidate.recommendation_status_rank = :cursorStatusRank
					        AND candidate.data_quality_score < :cursorScore
					    )
					    OR (
					        candidate.recommendation_status_rank = :cursorStatusRank
					        AND candidate.data_quality_score = :cursorScore
					        AND candidate.occurrence_id < :cursorOccurrenceId
					    )
					)
					""");
			} else {
				parameters.addValue("cursorEndDate", query.cursor().endDate());
				sql.append("""
					AND (
					    candidate.end_date > :cursorEndDate
					    OR (
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
				    candidate.data_quality_score DESC,
				    candidate.occurrence_id DESC
				""");
		} else {
			sql.append("""
				ORDER BY candidate.end_date ASC, candidate.occurrence_id DESC
				""");
		}
		sql.append("LIMIT :limit");

		return jdbcTemplate.query(sql.toString(), parameters, this::mapRow);
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
			.addValue("language", filter.language().name());
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
			travelStyle == null ? null : TravelStyle.valueOf(travelStyle),
			resultSet.getString("overview"),
			resultSet.getBigDecimal("data_quality_score"),
			resultSet.getInt("recommendation_status_rank"));
	}
}
