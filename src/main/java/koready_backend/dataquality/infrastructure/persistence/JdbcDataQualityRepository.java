package koready_backend.dataquality.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.dataquality.application.port.DataQualityRepository;

@Repository
public class JdbcDataQualityRepository implements DataQualityRepository {

	private static final String VISIBLE = "p.active = TRUE AND p.show_flag = TRUE";
	private static final String ADDRESS_PRESENT = """
		COALESCE(
		    NULLIF(TRIM(p.road_address), ''),
		    NULLIF(TRIM(p.address), ''),
		    NULLIF(TRIM(ko.address_text), '')
		) IS NOT NULL
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcDataQualityRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public DataQualityAggregate summarize() {
		PlaceAggregate places = jdbcTemplate.queryForObject(
			"""
			SELECT
			    COUNT(*) AS total_places,
			    COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) AS active_places,
			    COALESCE(SUM(CASE
			        WHEN %s AND NULLIF(TRIM(p.first_image_url), '') IS NULL THEN 1
			        ELSE 0 END), 0) AS missing_image,
			    COALESCE(SUM(CASE
			        WHEN %s AND NOT EXISTS (
			            SELECT 1
			            FROM place_localizations en
			            WHERE en.place_id = p.id AND en.language = 'EN'
			        ) THEN 1 ELSE 0 END), 0) AS missing_english,
			    COALESCE(SUM(CASE
			        WHEN %s AND (p.latitude IS NULL OR p.longitude IS NULL) THEN 1
			        ELSE 0 END), 0) AS missing_coordinates,
			    COALESCE(SUM(CASE
			        WHEN %s AND NOT (%s) THEN 1
			        ELSE 0 END), 0) AS missing_address,
			    COALESCE(SUM(CASE
			        WHEN %s
			         AND NULLIF(TRIM(ko.title), '') IS NOT NULL
			         AND %s
			         AND p.latitude IS NOT NULL
			         AND p.longitude IS NOT NULL
			         AND NULLIF(TRIM(p.first_image_url), '') IS NOT NULL
			         AND NULLIF(TRIM(p.service_region_code), '') IS NOT NULL
			        THEN 1 ELSE 0 END), 0) AS curation_ready
			FROM places p
			LEFT JOIN place_localizations ko
			    ON ko.place_id = p.id AND ko.language = 'KO'
			""".formatted(
				VISIBLE,
				VISIBLE,
				VISIBLE,
				VISIBLE,
				VISIBLE,
				ADDRESS_PRESENT,
				VISIBLE,
				ADDRESS_PRESENT),
			(resultSet, rowNumber) -> new PlaceAggregate(
				resultSet.getLong("total_places"),
				resultSet.getLong("active_places"),
				resultSet.getLong("missing_image"),
				resultSet.getLong("missing_english"),
				resultSet.getLong("missing_coordinates"),
				resultSet.getLong("missing_address"),
				resultSet.getLong("curation_ready")));

		LocalizationAggregate localization = jdbcTemplate.queryForObject(
			"""
			SELECT
			    COALESCE(SUM(CASE WHEN translation_source = 'KTO_EN' THEN 1 ELSE 0 END), 0)
			        AS kto_english,
			    COALESCE(SUM(CASE WHEN translation_source = 'AI_TRANSLATED' THEN 1 ELSE 0 END), 0)
			        AS ai_translated,
			    COALESCE(SUM(CASE WHEN translation_source = 'MANUAL_EDITED' THEN 1 ELSE 0 END), 0)
			        AS manual_edited
			FROM place_localizations
			""",
			(resultSet, rowNumber) -> new LocalizationAggregate(
				resultSet.getLong("kto_english"),
				resultSet.getLong("ai_translated"),
				resultSet.getLong("manual_edited")));

		Instant lastSuccessfulSyncAt = jdbcTemplate.queryForObject(
			"SELECT MAX(last_success_at) AS last_success_at FROM tour_api_sync_cursors",
			(resultSet, rowNumber) -> instant(resultSet, "last_success_at"));

		return new DataQualityAggregate(places, localization, lastSuccessfulSyncAt);
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
