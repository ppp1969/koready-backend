package koready_backend.buddy.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.buddy.application.port.ProfileImageRepository;

@Repository
public class JdbcProfileImageRepository implements ProfileImageRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcProfileImageRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void savePending(ImageRecord image) {
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_profile_images
			    (public_id, user_id, object_key, content_type, declared_size,
			     actual_size, status, created_at, completed_at)
			VALUES (?, ?, ?, ?, ?, NULL, 'PENDING', ?, NULL)
			""",
			image.imageId(),
			image.userId(),
			image.objectKey(),
			image.contentType(),
			image.declaredSize(),
			Timestamp.from(image.createdAt()));
	}

	@Override
	public Optional<ImageRecord> findOwned(String imageId, long userId) {
		return query(
			"""
			SELECT public_id, user_id, object_key, content_type, declared_size,
			       actual_size, status, created_at, completed_at
			FROM buddy_profile_images
			WHERE public_id = ? AND user_id = ?
			""",
			imageId,
			userId);
	}

	@Override
	public Optional<ImageRecord> findViewable(
		String imageId,
		String viewerPublicId
	) {
		return jdbcTemplate.query(
			"""
			SELECT image.public_id, image.user_id, image.object_key,
			       image.content_type, image.declared_size, image.actual_size,
			       image.status, image.created_at, image.completed_at
			FROM buddy_profile_images image
			JOIN users owner ON owner.id = image.user_id
			JOIN buddy_profiles profile ON profile.user_id = owner.id
			WHERE image.public_id = ?
			  AND image.status = 'READY'
			  AND profile.profile_image_url =
			      CONCAT('/api/v1/profile-images/', image.public_id)
			  AND (profile.profile_public = TRUE OR owner.public_id = ?)
			  AND owner.deleted_at IS NULL
			""",
			this::map,
			imageId,
			viewerPublicId == null ? "" : viewerPublicId).stream().findFirst();
	}

	@Override
	public void markReady(String imageId, long actualSize, Instant completedAt) {
		int updated = jdbcTemplate.update(
			"""
			UPDATE buddy_profile_images
			SET actual_size = ?, status = 'READY', completed_at = ?
			WHERE public_id = ? AND status = 'PENDING'
			""",
			actualSize,
			Timestamp.from(completedAt),
			imageId);
		if (updated != 1) {
			throw new IllegalStateException(
				"Profile image reservation changed before completion");
		}
	}

	private Optional<ImageRecord> query(String sql, Object... args) {
		List<ImageRecord> rows = jdbcTemplate.query(sql, this::map, args);
		return rows.stream().findFirst();
	}

	private ImageRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
		Long actualSize = resultSet.getObject("actual_size", Long.class);
		Timestamp completedAt = resultSet.getTimestamp("completed_at");
		return new ImageRecord(
			resultSet.getString("public_id"),
			resultSet.getLong("user_id"),
			resultSet.getString("object_key"),
			resultSet.getString("content_type"),
			resultSet.getLong("declared_size"),
			actualSize,
			ImageStatus.valueOf(resultSet.getString("status")),
			resultSet.getTimestamp("created_at").toInstant(),
			completedAt == null ? null : completedAt.toInstant());
	}
}
