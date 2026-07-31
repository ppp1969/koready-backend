package koready_backend.kto.infrastructure.persistence;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.kto.application.port.KtoDetailTargetSource;
import koready_backend.kto.domain.KtoDetailTarget;

@Repository
public class JdbcKtoDetailTargetSource implements KtoDetailTargetSource {

	private final JdbcTemplate jdbcTemplate;

	public JdbcKtoDetailTargetSource(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<KtoDetailTarget> findAfter(long placeId, int limit) {
		return jdbcTemplate.query(
			"""
			SELECT place.id, place.kto_content_id, place.kto_content_type_id
			FROM places place
			LEFT JOIN kto_place_detail_sync_status sync_status
			  ON sync_status.place_id = place.id
			WHERE place.id > ?
			  AND place.kto_content_id IS NOT NULL
			  AND place.kto_content_type_id IS NOT NULL
			  AND (
			      sync_status.place_id IS NULL
			      OR sync_status.next_refresh_at <= CURRENT_TIMESTAMP(6)
			  )
			ORDER BY
			    CASE WHEN EXISTS (
			        SELECT 1
			        FROM place_localizations publication_en
			        WHERE publication_en.place_id = place.id
			          AND publication_en.language = 'EN'
			          AND publication_en.translation_source
			              IN ('KTO_EN', 'MANUAL_EDITED')
			          AND NULLIF(TRIM(publication_en.title), '') IS NOT NULL
			    ) THEN 0 ELSE 1 END,
			    place.id ASC
			LIMIT ?
			""",
			(resultSet, rowNumber) -> new KtoDetailTarget(
				resultSet.getLong("id"),
				resultSet.getString("kto_content_id"),
				resultSet.getString("kto_content_type_id")),
			placeId,
			limit);
	}

	@Override
	public boolean existsAfter(long placeId) {
		Integer count = jdbcTemplate.queryForObject(
			"""
			SELECT EXISTS(
			    SELECT 1
			    FROM places place
			    LEFT JOIN kto_place_detail_sync_status sync_status
			      ON sync_status.place_id = place.id
			    WHERE place.id > ?
			      AND place.kto_content_id IS NOT NULL
			      AND place.kto_content_type_id IS NOT NULL
			      AND (
			          sync_status.place_id IS NULL
			          OR sync_status.next_refresh_at <= CURRENT_TIMESTAMP(6)
			      )
			)
			""",
			Integer.class,
			placeId);
		return count != null && count == 1;
	}
}
