package koready_backend.kto.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.kto.application.port.KtoDetailCoverageRepository;

@Repository
public class JdbcKtoDetailCoverageRepository
	implements KtoDetailCoverageRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcKtoDetailCoverageRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public CoverageAggregate summarize() {
		return jdbcTemplate.queryForObject(
			"""
			WITH gallery_counts AS (
			    SELECT place_id, COUNT(DISTINCT image_url_sha256) AS image_count
			    FROM place_images
			    GROUP BY place_id
			),
			coverage AS (
			    SELECT
			        place.id AS place_id,
			        CASE WHEN sync_status.place_id IS NULL THEN 0 ELSE 1 END
			            AS completed,
			        CASE
			            WHEN sync_status.next_refresh_at <= CURRENT_TIMESTAMP(6)
			            THEN 1 ELSE 0
			        END AS due_for_refresh,
			        sync_status.image_count AS kto_image_count,
			        CASE
			            WHEN sync_status.place_id IS NULL THEN NULL
			            WHEN COALESCE(gallery.image_count, 0) > 0
			                THEN LEAST(4, gallery.image_count)
			            WHEN NULLIF(TRIM(place.first_image_url), '') IS NOT NULL
			                THEN 1
			            ELSE 0
			        END AS gallery_image_count
			    FROM places place
			    LEFT JOIN kto_place_detail_sync_status sync_status
			      ON sync_status.place_id = place.id
			    LEFT JOIN gallery_counts gallery
			      ON gallery.place_id = place.id
			    WHERE place.kto_content_id IS NOT NULL
			      AND place.kto_content_type_id IS NOT NULL
			)
			SELECT
			    COUNT(*) AS total_places,
			    COALESCE(SUM(completed), 0) AS completed_places,
			    COALESCE(SUM(due_for_refresh), 0) AS due_for_refresh_places,
			    COALESCE(SUM(CASE
			        WHEN completed = 1 AND kto_image_count = 0 THEN 1 ELSE 0
			    END), 0) AS kto_zero,
			    COALESCE(SUM(CASE
			        WHEN completed = 1 AND kto_image_count = 1 THEN 1 ELSE 0
			    END), 0) AS kto_one,
			    COALESCE(SUM(CASE
			        WHEN completed = 1 AND kto_image_count = 2 THEN 1 ELSE 0
			    END), 0) AS kto_two,
			    COALESCE(SUM(CASE
			        WHEN completed = 1 AND kto_image_count = 3 THEN 1 ELSE 0
			    END), 0) AS kto_three,
			    COALESCE(SUM(CASE
			        WHEN completed = 1 AND kto_image_count >= 4 THEN 1 ELSE 0
			    END), 0) AS kto_four_or_more,
			    COALESCE(SUM(CASE
			        WHEN completed = 1 AND gallery_image_count = 0 THEN 1 ELSE 0
			    END), 0) AS gallery_zero,
			    COALESCE(SUM(CASE
			        WHEN completed = 1 AND gallery_image_count = 1 THEN 1 ELSE 0
			    END), 0) AS gallery_one,
			    COALESCE(SUM(CASE
			        WHEN completed = 1 AND gallery_image_count = 2 THEN 1 ELSE 0
			    END), 0) AS gallery_two,
			    COALESCE(SUM(CASE
			        WHEN completed = 1 AND gallery_image_count = 3 THEN 1 ELSE 0
			    END), 0) AS gallery_three,
			    COALESCE(SUM(CASE
			        WHEN completed = 1 AND gallery_image_count >= 4 THEN 1 ELSE 0
			    END), 0) AS gallery_four_or_more
			FROM coverage
			""",
			(resultSet, rowNumber) -> new CoverageAggregate(
				resultSet.getLong("total_places"),
				resultSet.getLong("completed_places"),
				resultSet.getLong("due_for_refresh_places"),
				new ImageBuckets(
					resultSet.getLong("kto_zero"),
					resultSet.getLong("kto_one"),
					resultSet.getLong("kto_two"),
					resultSet.getLong("kto_three"),
					resultSet.getLong("kto_four_or_more")),
				new ImageBuckets(
					resultSet.getLong("gallery_zero"),
					resultSet.getLong("gallery_one"),
					resultSet.getLong("gallery_two"),
					resultSet.getLong("gallery_three"),
					resultSet.getLong("gallery_four_or_more"))));
	}
}
