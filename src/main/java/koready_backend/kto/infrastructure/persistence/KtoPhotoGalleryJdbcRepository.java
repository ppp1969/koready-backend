package koready_backend.kto.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.exception.KtoPhotoGalleryMappingConflictException;
import koready_backend.kto.application.exception.KtoSnapshotConflictException;
import koready_backend.kto.application.model.KtoPhotoGalleryStorePageCommand;
import koready_backend.kto.application.model.KtoPhotoGalleryStorePageResult;
import koready_backend.kto.application.port.KtoPhotoGalleryCurationRepository;
import koready_backend.kto.application.port.KtoPhotoGalleryStore;
import koready_backend.kto.domain.KtoPhotoGalleryItem;

@Repository
public class KtoPhotoGalleryJdbcRepository
	implements KtoPhotoGalleryStore,
	KtoPhotoGalleryCurationRepository {

	private static final String ENDPOINT =
		"https://apis.data.go.kr/B551011/PhotoGalleryService1/galleryList1";

	private final JdbcTemplate jdbcTemplate;

	public KtoPhotoGalleryJdbcRepository(
		JdbcTemplate jdbcTemplate
	) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public KtoPhotoGalleryStorePageResult store(
		KtoPhotoGalleryStorePageCommand command
	) {
		ExistingSnapshot existing =
			findSnapshot(command.snapshot().storageKey());
		if (existing != null) {
			if (!existing.rawHash().equals(
					command.page().responseSha256())
				|| existing.itemCount()
					!= command.page().items().size()) {
				throw new KtoSnapshotConflictException();
			}
			return new KtoPhotoGalleryStorePageResult(
				existing.callLogId(),
				existing.snapshotId(),
				existing.itemCount(),
				true);
		}

		long callLogId = insertCallLog(command);
		long snapshotId = insertSnapshot(command, callLogId);
		upsertImages(command, snapshotId);
		refreshMappedImages(command.page().items().stream()
			.map(KtoPhotoGalleryItem::contentId)
			.toList());
		return new KtoPhotoGalleryStorePageResult(
			callLogId,
			snapshotId,
			command.page().items().size(),
			false);
	}

	@Override
	public java.util.Optional<PhotoGalleryRecord> findByContentId(
		String contentId
	) {
		List<PhotoGalleryRecord> rows = jdbcTemplate.query(
			selectSql() + " WHERE image.content_id = ?",
			this::mapImage,
			contentId);
		return rows.stream().findFirst();
	}

	@Override
	public boolean placeExists(long placeId) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM places WHERE id = ?",
			Integer.class,
			placeId);
		return count != null && count == 1;
	}

	@Override
	public void saveMapping(MappingRecord mapping) {
		try {
			int updated = jdbcTemplate.update(
				"""
				UPDATE kto_photo_gallery_place_mappings
				SET place_id = ?,
				    display_order = ?,
				    approved_by_subject = ?,
				    approval_reason = ?,
				    approved_at = ?
				WHERE photo_gallery_id = ?
				""",
				mapping.placeId(),
				mapping.displayOrder(),
				mapping.actorSubject(),
				mapping.reason(),
				Timestamp.from(mapping.approvedAt()),
				mapping.photoGalleryId());
			if (updated == 0) {
				jdbcTemplate.update(
					"""
					INSERT INTO kto_photo_gallery_place_mappings
					    (photo_gallery_id, place_id, display_order,
					     approved_by_subject, approval_reason,
					     approved_at)
					VALUES (?, ?, ?, ?, ?, ?)
					""",
					mapping.photoGalleryId(),
					mapping.placeId(),
					mapping.displayOrder(),
					mapping.actorSubject(),
					mapping.reason(),
					Timestamp.from(mapping.approvedAt()));
			}
		} catch (DuplicateKeyException exception) {
			throw new KtoPhotoGalleryMappingConflictException();
		}
		refreshMappedImages(List.of(mapping.contentId()));
	}

	@Override
	public boolean removeMapping(String contentId) {
		jdbcTemplate.update(
			"""
			DELETE FROM place_images
			WHERE source_type = 'KTO_PHOTO_GALLERY'
			  AND source_content_id = ?
			""",
			contentId);
		return jdbcTemplate.update(
			"""
			DELETE mapping
			FROM kto_photo_gallery_place_mappings mapping
			JOIN kto_photo_gallery_images image
			    ON image.id = mapping.photo_gallery_id
			WHERE image.content_id = ?
			""",
			contentId) == 1;
	}

	@Override
	public void recordAudit(AuditRecord audit) {
		jdbcTemplate.update(
			"""
			INSERT INTO admin_audit_logs
			    (actor_subject, action, resource_type, resource_id,
			     reason, created_at)
			VALUES (?, ?, 'KTO_PHOTO_GALLERY_MAPPING', ?, ?, ?)
			""",
			audit.actorSubject(),
			audit.action(),
			audit.contentId(),
			audit.reason(),
			Timestamp.from(audit.createdAt()));
	}

	@Override
	public List<PhotoGalleryRecord> findPage(
		PhotoGalleryQuery query
	) {
		StringBuilder sql = new StringBuilder(selectSql());
		List<Object> parameters = new ArrayList<>();
		sql.append(" WHERE image.id > ?");
		parameters.add(query.startAfterId());
		if (query.query() != null) {
			sql.append("""

				  AND (
				      image.title LIKE ?
				      OR image.photography_location LIKE ?
				      OR image.search_keyword LIKE ?
				      OR image.photographer LIKE ?
				  )
				""");
			String pattern = "%" + query.query() + "%";
			parameters.add(pattern);
			parameters.add(pattern);
			parameters.add(pattern);
			parameters.add(pattern);
		}
		if (query.mapped() != null) {
			sql.append(query.mapped()
				? " AND mapping.id IS NOT NULL"
				: " AND mapping.id IS NULL");
		}
		sql.append(" ORDER BY image.id ASC LIMIT ?");
		parameters.add(query.limit());
		return jdbcTemplate.query(
			sql.toString(),
			this::mapImage,
			parameters.toArray());
	}

	private ExistingSnapshot findSnapshot(String storageKey) {
		List<ExistingSnapshot> rows = jdbcTemplate.query(
			"""
			SELECT snapshot.id, snapshot.call_log_id,
			       snapshot.raw_content_sha256, snapshot.item_count
			FROM open_api_raw_snapshots snapshot
			WHERE snapshot.storage_key = ?
			""",
			(resultSet, rowNumber) -> new ExistingSnapshot(
				resultSet.getLong("id"),
				resultSet.getLong("call_log_id"),
				resultSet.getString("raw_content_sha256"),
				resultSet.getInt("item_count")),
			storageKey);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private long insertCallLog(
		KtoPhotoGalleryStorePageCommand command
	) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_call_logs
				    (provider, api_name, operation, endpoint,
				     request_started_at, response_received_at,
				     duration_ms, success, http_status,
				     request_params_masked, response_summary,
				     external_result_code, related_job_id,
				     related_job_item_id, item_count, response_bytes)
				VALUES (
				    'KTO', 'PHOTO_GALLERY', 'galleryList1', ?,
				    ?, ?, ?, TRUE, ?,
				    JSON_OBJECT(
				        'numOfRows', ?, 'pageNo', ?,
				        'MobileOS', 'ETC',
				        'MobileApp', 'KoReady',
				        '_type', 'json', 'serviceKey', '***'),
				    JSON_OBJECT(
				        'resultCode', '0000', 'pageNo', ?,
				        'numOfRows', ?, 'totalCount', ?,
				        'responseSha256', ?),
				    '0000', ?, ?, ?, ?)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, ENDPOINT);
			statement.setTimestamp(
				2, Timestamp.from(command.call().requestedAt()));
			statement.setTimestamp(
				3, Timestamp.from(
					command.call().responseReceivedAt()));
			statement.setLong(4, command.call().durationMs());
			statement.setInt(5, command.call().httpStatus());
			statement.setInt(6, command.page().pageSize());
			statement.setInt(7, command.page().pageNumber());
			statement.setInt(8, command.page().pageNumber());
			statement.setInt(9, command.page().pageSize());
			statement.setInt(10, command.page().totalCount());
			statement.setString(
				11, command.page().responseSha256());
			if (command.batchExecution() == null) {
				statement.setNull(12, java.sql.Types.BIGINT);
				statement.setNull(13, java.sql.Types.BIGINT);
			} else {
				statement.setLong(
					12, command.batchExecution().jobId());
				statement.setLong(
					13, command.batchExecution().jobItemId());
			}
			statement.setInt(
				14, command.page().items().size());
			statement.setLong(15, command.page().responseBytes());
			return statement;
		}, keyHolder);
		return requiredKey(keyHolder);
	}

	private long insertSnapshot(
		KtoPhotoGalleryStorePageCommand command,
		long callLogId
	) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_raw_snapshots
				    (call_log_id, provider, api_name, operation,
				     storage_key, storage_format, content_type,
				     raw_content_sha256, stored_object_sha256,
				     byte_size, compressed_byte_size, item_count,
				     captured_at, retention_class, immutable)
				VALUES (
				    ?, 'KTO', 'PHOTO_GALLERY', 'galleryList1', ?,
				    'JSON_GZIP', 'application/json', ?, ?, ?, ?, ?,
				    ?, 'PROVIDER_RESTRICTED', TRUE)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setLong(1, callLogId);
			statement.setString(
				2, command.snapshot().storageKey());
			statement.setString(
				3, command.page().responseSha256());
			statement.setString(
				4, command.snapshot().storedObjectSha256());
			statement.setLong(5, command.page().responseBytes());
			statement.setLong(
				6, command.snapshot().compressedByteSize());
			statement.setInt(
				7, command.page().items().size());
			statement.setTimestamp(
				8, Timestamp.from(command.snapshot().capturedAt()));
			return statement;
		}, keyHolder);
		return requiredKey(keyHolder);
	}

	private void upsertImages(
		KtoPhotoGalleryStorePageCommand command,
		long snapshotId
	) {
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO kto_photo_gallery_images
			    (content_id, content_type_id, title,
			     photography_location, photography_month,
			     photographer, search_keyword, image_url,
			     source_created_time, source_modified_time,
			     source_hash, raw_snapshot_id, source_captured_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE
			    content_type_id = VALUES(content_type_id),
			    title = VALUES(title),
			    photography_location = VALUES(photography_location),
			    photography_month = VALUES(photography_month),
			    photographer = VALUES(photographer),
			    search_keyword = VALUES(search_keyword),
			    image_url = VALUES(image_url),
			    source_created_time = VALUES(source_created_time),
			    source_modified_time = VALUES(source_modified_time),
			    source_hash = VALUES(source_hash),
			    raw_snapshot_id = VALUES(raw_snapshot_id),
			    source_captured_at = VALUES(source_captured_at)
			""",
			command.page().items(),
			50,
			(statement, item) -> {
				statement.setString(1, item.contentId());
				statement.setString(2, item.contentTypeId());
				statement.setString(3, item.title());
				statement.setString(
					4, item.photographyLocation());
				statement.setString(
					5, item.photographyMonth());
				statement.setString(6, item.photographer());
				statement.setString(7, item.searchKeyword());
				statement.setString(8, item.imageUrl());
				statement.setString(9, item.createdTime());
				statement.setString(10, item.modifiedTime());
				statement.setString(11, item.sourceHash());
				statement.setLong(12, snapshotId);
				statement.setTimestamp(
					13,
					Timestamp.from(
						command.snapshot().capturedAt()));
			});
	}

	private void refreshMappedImages(List<String> contentIds) {
		if (contentIds.isEmpty()) {
			return;
		}
		String placeholders = String.join(
			",",
			java.util.Collections.nCopies(
				contentIds.size(), "?"));
		jdbcTemplate.update(
			"""
			DELETE FROM place_images
			WHERE source_type = 'KTO_PHOTO_GALLERY'
			  AND source_content_id IN (%s)
			""".formatted(placeholders),
			contentIds.toArray());
		jdbcTemplate.update(
			"""
			INSERT INTO place_images
			    (place_id, image_url, image_url_sha256,
			     source_type, source_priority, source_order,
			     source_content_id, source_image_name,
			     copyright_type)
			SELECT
			    mapping.place_id,
			    image.image_url,
			    SHA2(image.image_url, 256),
			    'KTO_PHOTO_GALLERY',
			    250,
			    mapping.display_order,
			    image.content_id,
			    image.title,
			    'OPERATOR_REVIEWED'
			FROM kto_photo_gallery_place_mappings mapping
			JOIN kto_photo_gallery_images image
			    ON image.id = mapping.photo_gallery_id
			WHERE image.content_id IN (%s)
			""".formatted(placeholders),
			contentIds.toArray());
	}

	private String selectSql() {
		return """
			SELECT
			    image.id,
			    image.content_id,
			    image.content_type_id,
			    image.title,
			    image.photography_location,
			    image.photography_month,
			    image.photographer,
			    image.search_keyword,
			    image.image_url,
			    CASE
			        WHEN mapping.id IS NULL THEN image.rights_status
			        ELSE 'OPERATOR_APPROVED'
			    END AS rights_status,
			    mapping.place_id AS mapped_place_id,
			    localization.title AS mapped_place_title_ko,
			    mapping.display_order,
			    mapping.approved_by_subject,
			    mapping.approval_reason,
			    mapping.approved_at,
			    image.source_captured_at
			FROM kto_photo_gallery_images image
			LEFT JOIN kto_photo_gallery_place_mappings mapping
			    ON mapping.photo_gallery_id = image.id
			LEFT JOIN place_localizations localization
			    ON localization.place_id = mapping.place_id
			   AND localization.language = 'KO'
			""";
	}

	private PhotoGalleryRecord mapImage(
		ResultSet resultSet,
		int rowNumber
	) throws SQLException {
		return new PhotoGalleryRecord(
			resultSet.getLong("id"),
			resultSet.getString("content_id"),
			resultSet.getString("content_type_id"),
			resultSet.getString("title"),
			resultSet.getString("photography_location"),
			resultSet.getString("photography_month"),
			resultSet.getString("photographer"),
			resultSet.getString("search_keyword"),
			resultSet.getString("image_url"),
			resultSet.getString("rights_status"),
			nullableLong(resultSet, "mapped_place_id"),
			resultSet.getString("mapped_place_title_ko"),
			nullableInteger(resultSet, "display_order"),
			resultSet.getString("approved_by_subject"),
			resultSet.getString("approval_reason"),
			instant(resultSet, "approved_at"),
			instant(resultSet, "source_captured_at"));
	}

	private static long requiredKey(
		GeneratedKeyHolder keyHolder
	) {
		Number key = keyHolder.getKey();
		if (key == null || key.longValue() <= 0) {
			throw new IllegalStateException(
				"Photo gallery database key was not generated");
		}
		return key.longValue();
	}

	private static Long nullableLong(
		ResultSet resultSet,
		String column
	) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	private static Integer nullableInteger(
		ResultSet resultSet,
		String column
	) throws SQLException {
		int value = resultSet.getInt(column);
		return resultSet.wasNull() ? null : value;
	}

	private static java.time.Instant instant(
		ResultSet resultSet,
		String column
	) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private record ExistingSnapshot(
		long snapshotId,
		long callLogId,
		String rawHash,
		int itemCount
	) {
	}
}
