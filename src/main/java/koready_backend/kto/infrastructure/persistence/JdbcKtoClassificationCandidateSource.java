package koready_backend.kto.infrastructure.persistence;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.model.KtoClassificationCandidate;
import koready_backend.kto.application.port.KtoClassificationCandidateSource;
import koready_backend.place.domain.TravelStyle;

@Repository
public class JdbcKtoClassificationCandidateSource
	implements KtoClassificationCandidateSource {

	private static final String SELECT_CANDIDATES = """
		SELECT
		    place.id,
		    place.kto_content_id,
		    COALESCE(
		        (
		            SELECT localization.title
		            FROM place_localizations localization
		            WHERE localization.place_id = place.id
		              AND localization.language = 'KO'
		            LIMIT 1
		        ),
		        place.kto_content_id
		    ) AS title,
		    place.kto_content_type_id,
		    place.lcls_systm1,
		    place.lcls_systm2,
		    place.lcls_systm3,
		    (
		        NULLIF(TRIM(place.first_image_url), '') IS NOT NULL
		        OR EXISTS (
		            SELECT 1
		            FROM place_images image
		            WHERE image.place_id = place.id
		        )
		    ) AS has_image,
		    (place.active = TRUE AND place.show_flag = TRUE) AS currently_published,
		    (
		        SELECT GROUP_CONCAT(
		            mapping.travel_style
		            ORDER BY mapping.travel_style
		            SEPARATOR ','
		        )
		        FROM place_style_mappings mapping
		        WHERE mapping.place_id = place.id
		          AND mapping.source = 'MANUAL'
		    ) AS manual_styles
		FROM places place
		WHERE place.kto_content_id IS NOT NULL
		  AND place.id > :afterPlaceId
		ORDER BY place.id ASC
		LIMIT :limit
		""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public JdbcKtoClassificationCandidateSource(
		NamedParameterJdbcTemplate jdbcTemplate
	) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional(readOnly = true)
	public List<KtoClassificationCandidate> findAfter(long placeId, int limit) {
		if (placeId < 0) {
			throw new IllegalArgumentException("Place id cursor cannot be negative");
		}
		if (limit < 1 || limit > 1_000) {
			throw new IllegalArgumentException("Limit must be between 1 and 1000");
		}
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("afterPlaceId", placeId)
			.addValue("limit", limit);
		return jdbcTemplate.query(
			SELECT_CANDIDATES,
			parameters,
			(resultSet, rowNumber) -> new KtoClassificationCandidate(
				resultSet.getLong("id"),
				resultSet.getString("kto_content_id"),
				resultSet.getString("title"),
				resultSet.getString("kto_content_type_id"),
				resultSet.getString("lcls_systm1"),
				resultSet.getString("lcls_systm2"),
				resultSet.getString("lcls_systm3"),
				resultSet.getBoolean("has_image"),
				resultSet.getBoolean("currently_published"),
				parseStyles(resultSet.getString("manual_styles"))));
	}

	private Set<TravelStyle> parseStyles(String rawStyles) {
		if (rawStyles == null || rawStyles.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(rawStyles.split(","))
			.map(String::trim)
			.filter(style -> !style.isEmpty())
			.map(TravelStyle::valueOf)
			.collect(Collectors.toUnmodifiableSet());
	}
}
