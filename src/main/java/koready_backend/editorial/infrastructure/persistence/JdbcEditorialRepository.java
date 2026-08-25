package koready_backend.editorial.infrastructure.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.editorial.application.exception.EditorialPlaceNotFoundException;
import koready_backend.editorial.application.port.EditorialRepository;
import koready_backend.editorial.application.port.EditorialRepository.PlaceVisibilityRecord;
import koready_backend.editorial.application.port.EditorialRepository.VisibilityCommand;
import koready_backend.editorial.application.port.EditorialRepository.ImageOrderCommand;
import koready_backend.editorial.application.port.EditorialRepository.PlaceImageOrderRecord;
import koready_backend.editorial.application.port.EditorialRepository.PlaceImageRecord;
import koready_backend.editorial.application.port.EditorialRepository.PlacePriorityRecord;
import koready_backend.editorial.application.port.EditorialRepository.PriorityCommand;
import koready_backend.editorial.domain.EditorialJobPriority;
import koready_backend.editorial.domain.EditorialJobStatus;
import koready_backend.editorial.domain.EditorialTriggerType;
import koready_backend.editorial.domain.EditorialLanguage;
import koready_backend.editorial.domain.TourismPurposeTag;
import koready_backend.editorial.domain.EditorialCandidateSourceTrack;

@Repository
public class JdbcEditorialRepository implements EditorialRepository {

	static final String SOURCE_FINGERPRINT = """
		SHA2(CONCAT_WS('|',
			COALESCE(CAST(p.source_modified_time AS CHAR), ''),
			COALESCE(ko.source_hash, ''),
			COALESCE(en.source_hash, ''),
			COALESCE((SELECT GROUP_CONCAT(
				COALESCE(i.image_url_sha256, SHA2(i.image_url, 256))
				ORDER BY i.source_priority DESC, i.source_order, i.id SEPARATOR ',')
				FROM place_images i WHERE i.place_id = p.id), ''),
			COALESCE((SELECT GROUP_CONCAT(
				CONCAT(s.travel_style, ':', COALESCE(s.rule_version, ''))
				ORDER BY s.travel_style SEPARATOR ',')
				FROM place_style_mappings s WHERE s.place_id = p.id), '')
		), 256)
		""";

	private static final String CANDIDATE_FROM_SQL = """
		FROM places p
		JOIN place_localizations ko ON ko.place_id = p.id AND ko.language = 'KO'
		LEFT JOIN place_localizations en ON en.place_id = p.id AND en.language = 'EN'
		  AND en.translation_source IN ('KTO_EN', 'MANUAL_EDITED')
		LEFT JOIN place_editorial_jobs latest ON latest.id = (
		  SELECT j.id FROM place_editorial_jobs j WHERE j.place_id = p.id
		  ORDER BY j.requested_at DESC, j.id DESC LIMIT 1)
		WHERE p.active = TRUE
		  AND EXISTS (SELECT 1 FROM place_style_mappings s WHERE s.place_id = p.id)
		  AND (NULLIF(TRIM(p.first_image_url), '') IS NOT NULL
		       OR EXISTS (SELECT 1 FROM place_images i WHERE i.place_id = p.id))
		""";

	private static final String QUEUE_ELIGIBLE_SQL = """
		(NULLIF(TRIM(ko.overview), '') IS NOT NULL
		 AND COALESCE(latest.status, 'NOT_REQUESTED') IN ('NOT_REQUESTED', 'FAILED', 'STALE'))
		""";

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;

	public JdbcEditorialRepository(
		JdbcTemplate jdbcTemplate,
		NamedParameterJdbcTemplate namedJdbcTemplate
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.namedJdbcTemplate = namedJdbcTemplate;
	}

	@Override
	public EnqueueRecord enqueue(EnqueueCommand command) {
		Source source = findSource(command.placeId())
			.orElseThrow(() -> new EditorialPlaceNotFoundException(command.placeId()));
		String requestKey = sha256(
			command.placeId() + "|" + source.fingerprint() + "|" + command.promptVersion());
		Optional<EnqueueRecord> existing = findEnqueueByRequestKey(requestKey);
		String publicId = existing.map(EnqueueRecord::jobId)
			.orElseGet(() -> UUID.randomUUID().toString());

		jdbcTemplate.update("""
			INSERT INTO place_editorial_jobs
			    (public_id, place_id, request_key, source_fingerprint, prompt_version,
			     trigger_type, priority, status, requested_by_subject, requested_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, 'QUEUED', ?, ?)
			ON DUPLICATE KEY UPDATE
			    trigger_type = IF(VALUES(priority) > priority,
			        VALUES(trigger_type), trigger_type),
			    priority = GREATEST(priority, VALUES(priority)),
			    requested_by_subject = COALESCE(VALUES(requested_by_subject), requested_by_subject),
			    started_at = IF(status IN ('FAILED', 'STALE'), NULL, started_at),
			    completed_at = IF(status IN ('FAILED', 'STALE'), NULL, completed_at),
			    attempt_count = IF(status IN ('FAILED', 'STALE'), 0, attempt_count),
			    lease_token = IF(status IN ('FAILED', 'STALE'), NULL, lease_token),
			    lease_expires_at = IF(status IN ('FAILED', 'STALE'), NULL, lease_expires_at),
			    next_attempt_at = IF(status IN ('FAILED', 'STALE'), NULL, next_attempt_at),
			    status = IF(status IN ('READY', 'PROCESSING'), status, 'QUEUED'),
			    error_code = IF(status IN ('READY', 'PROCESSING'), error_code, NULL),
			    error_message = IF(status IN ('READY', 'PROCESSING'), error_message, NULL)
			""",
			publicId,
			command.placeId(),
			requestKey,
			source.fingerprint(),
			command.promptVersion(),
			command.triggerType().name(),
			command.priority().weight(),
			command.requestedBySubject(),
			Timestamp.from(command.requestedAt()));

		EnqueueRecord saved = findEnqueueByRequestKey(requestKey).orElseThrow();
		if (existing.isEmpty()) {
			jdbcTemplate.update("""
				INSERT INTO place_editorial_audits
				    (place_id, job_id, actor_subject, action, details_json, created_at)
				SELECT place_id, id, requested_by_subject, 'EDITORIAL_QUEUED',
				       JSON_OBJECT('triggerType', trigger_type, 'priority', priority), ?
				FROM place_editorial_jobs WHERE request_key = ?
				""", Timestamp.from(command.requestedAt()), requestKey);
		}
		return new EnqueueRecord(
			saved.jobId(), saved.placeId(), saved.status(), saved.priority(),
			saved.triggerType(), saved.requestedAt(), existing.isEmpty());
	}

	@Override
	public Optional<ReadyContentRecord> findReady(
		long placeId,
		EditorialLanguage language,
		String promptVersion
	) {
		List<ReadyBase> rows = namedJdbcTemplate.query("""
			SELECT content.id, content.place_id, content.source_fingerprint,
			       content.prompt_version, localized.topic,
			       localized.one_line_description, localized.short_introduction,
			       content.generated_at
			FROM places p
			LEFT JOIN place_localizations ko
			  ON ko.place_id = p.id AND ko.language = 'KO'
			LEFT JOIN place_localizations en
			  ON en.place_id = p.id AND en.language = 'EN'
			JOIN place_editorial_contents content
			  ON content.place_id = p.id
			 AND content.status = 'READY'
			 AND content.prompt_version = :promptVersion
			 AND content.source_fingerprint = """ + SOURCE_FINGERPRINT + """
			JOIN place_editorial_localizations localized
			  ON localized.editorial_content_id = content.id
			 AND localized.language = :language
			WHERE p.id = :placeId
			ORDER BY content.generated_at DESC
			LIMIT 1
			""", Map.of(
			"placeId", placeId,
			"language", language.name(),
			"promptVersion", promptVersion),
			(rs, rowNumber) -> new ReadyBase(
				rs.getLong("id"),
				rs.getLong("place_id"),
				rs.getString("source_fingerprint"),
				rs.getString("prompt_version"),
				rs.getString("topic"),
				rs.getString("one_line_description"),
				rs.getString("short_introduction"),
				instant(rs, "generated_at")));
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		ReadyBase base = rows.getFirst();
		List<String> enjoyPoints = jdbcTemplate.queryForList("""
			SELECT content FROM place_editorial_enjoy_points
			WHERE editorial_content_id = ? AND language = ?
			ORDER BY display_order
			""", String.class, base.id(), language.name());
		List<TourismPurposeTag> tags = jdbcTemplate.queryForList("""
			SELECT tag_code FROM place_editorial_tags
			WHERE editorial_content_id = ? ORDER BY display_order
			""", String.class, base.id()).stream()
			.map(TourismPurposeTag::valueOf).toList();
		return Optional.of(new ReadyContentRecord(
			base.placeId(), base.sourceFingerprint(), base.promptVersion(),
			base.topic(), base.oneLineDescription(), base.shortIntroduction(),
			enjoyPoints, tags, base.generatedAt()));
	}

	@Override
	public Optional<JobRecord> findLatestJob(long placeId, String promptVersion) {
		return jdbcTemplate.query("""
			SELECT * FROM place_editorial_jobs
			WHERE place_id = ? AND prompt_version = ?
			ORDER BY requested_at DESC, id DESC LIMIT 1
			""", this::job, placeId, promptVersion).stream().findFirst();
	}

	@Override
	public List<CandidateRecord> findCandidates(CandidateQuery query) {
		StringBuilder sql = new StringBuilder("""
			SELECT p.id AS place_id, ko.title AS title_ko, en.title AS title_en,
			       p.service_region_code, p.active, p.show_flag, p.curation_priority,
			       COALESCE((SELECT i.image_url FROM place_images i
			          WHERE i.place_id = p.id
			          ORDER BY i.admin_display_order IS NULL, i.admin_display_order,
			                   i.source_priority DESC, i.source_order, i.id LIMIT 1),
			          NULLIF(TRIM(p.first_image_url), '')) AS image_url,
			       (NULLIF(TRIM(ko.overview), '') IS NOT NULL) AS has_ko_overview,
			       %s AS queue_eligible,
			       COALESCE(latest.status, 'NOT_REQUESTED') AS editorial_status,
			       latest.requested_at
			%s
			""".formatted(QUEUE_ELIGIBLE_SQL, CANDIDATE_FROM_SQL));
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("cursor", query.startAfterPlaceId())
			.addValue("limit", query.limit());
		appendCandidateFilters(sql, params, query);
		sql.append(" ORDER BY p.curation_priority DESC, p.id LIMIT :limit OFFSET :cursor");
		return namedJdbcTemplate.query(sql.toString(), params, this::candidate);
	}

	@Override
	public long countCandidates(CandidateQuery query) {
		StringBuilder sql = new StringBuilder("SELECT COUNT(*) " + CANDIDATE_FROM_SQL);
		MapSqlParameterSource params = new MapSqlParameterSource();
		appendCandidateFilters(sql, params, query);
		Long count = namedJdbcTemplate.queryForObject(sql.toString(), params, Long.class);
		return count == null ? 0 : count;
	}

	private void appendCandidateFilters(
		StringBuilder sql,
		MapSqlParameterSource params,
		CandidateQuery query
	) {
		if (query.query() != null) {
			sql.append(" AND (ko.title LIKE :query OR en.title LIKE :query");
			params.addValue("query", "%" + query.query() + "%");
			try {
				params.addValue("queryPlaceId", Long.parseLong(query.query()));
				sql.append(" OR p.id = :queryPlaceId");
			} catch (NumberFormatException ignored) {
				// Non-numeric search terms only match localized titles.
			}
			sql.append(")");
		}
		if (query.status() != null) {
			switch (query.status()) {
				case NOT_REQUESTED -> sql.append(
					" AND COALESCE(latest.status, 'NOT_REQUESTED') = 'NOT_REQUESTED'");
				case IN_PROGRESS -> sql.append(
					" AND latest.status IN ('QUEUED', 'PROCESSING')");
				case READY -> sql.append(" AND latest.status = 'READY'");
				case RETRYABLE -> sql.append(" AND latest.status IN ('FAILED', 'STALE')");
			}
		}
		if (query.region() != null) {
			sql.append(" AND p.service_region_code = :region");
			params.addValue("region", query.region().name());
		}
		if (query.hasKoreanOverview() != null) {
			sql.append(query.hasKoreanOverview()
				? " AND NULLIF(TRIM(ko.overview), '') IS NOT NULL"
				: " AND NULLIF(TRIM(ko.overview), '') IS NULL");
		}
		if (query.queueEligible() != null) {
			sql.append(query.queueEligible()
				? " AND " + QUEUE_ELIGIBLE_SQL
				: " AND NOT " + QUEUE_ELIGIBLE_SQL);
		}
		switch (query.sourceTrack()) {
			case KTO_BILINGUAL -> sql.append(" AND en.place_id IS NOT NULL");
			case KOREAN_ONLY_AI -> sql.append(" AND en.place_id IS NULL");
			case ALL -> { }
		}
	}

	@Override
	public Optional<CandidateDetailRecord> findCandidate(long placeId) {
		List<CandidateDetailBase> rows = jdbcTemplate.query("""
			SELECT p.id AS place_id, ko.title AS title_ko, en.title AS title_en,
			       ko.overview AS overview_ko,
			       COALESCE(ko.address_text, p.road_address, p.address) AS address,
			       p.service_region_code, p.active, p.show_flag, p.curation_priority,
			       COALESCE(latest.status, 'NOT_REQUESTED') AS editorial_status,
			       latest.requested_at
			FROM places p
			JOIN place_localizations ko ON ko.place_id = p.id AND ko.language = 'KO'
			LEFT JOIN place_localizations en ON en.place_id = p.id AND en.language = 'EN'
			  AND en.translation_source IN ('KTO_EN', 'MANUAL_EDITED')
			LEFT JOIN place_editorial_jobs latest ON latest.id = (
			  SELECT j.id FROM place_editorial_jobs j WHERE j.place_id = p.id
			  ORDER BY j.requested_at DESC, j.id DESC LIMIT 1)
			WHERE p.id = ? AND p.active = TRUE
			  AND EXISTS (SELECT 1 FROM place_style_mappings s WHERE s.place_id = p.id)
			  AND (NULLIF(TRIM(p.first_image_url), '') IS NOT NULL
			       OR EXISTS (SELECT 1 FROM place_images i WHERE i.place_id = p.id))
			""", (rs, rowNumber) -> new CandidateDetailBase(
				rs.getLong("place_id"), rs.getString("title_ko"),
				rs.getString("title_en"), rs.getString("overview_ko"),
				rs.getString("address"),
				rs.getString("service_region_code"),
				rs.getBoolean("active"), rs.getBoolean("show_flag"),
				rs.getInt("curation_priority"),
				EditorialJobStatus.valueOf(rs.getString("editorial_status")),
				instant(rs, "requested_at")), placeId);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		CandidateDetailBase base = rows.getFirst();
		List<PlaceImageRecord> orderedImages = jdbcTemplate.query("""
			SELECT id, image_url,
			       ROW_NUMBER() OVER (
			         ORDER BY admin_display_order IS NULL, admin_display_order,
			                  source_priority DESC, source_order, id) AS effective_order
			FROM place_images
			WHERE place_id = ? AND NULLIF(TRIM(image_url), '') IS NOT NULL
			ORDER BY effective_order
			""", (rs, rowNumber) -> new PlaceImageRecord(
				rs.getLong("id"), rs.getString("image_url"), rs.getInt("effective_order")),
			placeId);
		List<String> images = orderedImages.stream()
			.map(PlaceImageRecord::imageUrl).distinct().limit(4).toList();
		if (images.isEmpty()) {
			images = jdbcTemplate.queryForList("""
				SELECT first_image_url FROM places
				WHERE id = ? AND NULLIF(TRIM(first_image_url), '') IS NOT NULL
				""", String.class, placeId);
		}
		List<String> styles = jdbcTemplate.queryForList("""
			SELECT travel_style FROM place_style_mappings
			WHERE place_id = ? ORDER BY is_primary DESC, confidence DESC, travel_style
			""", String.class, placeId);
		return Optional.of(new CandidateDetailRecord(
			base.placeId(), base.titleKo(), base.titleEn(), base.overviewKo(),
			base.address(), base.region(), images, orderedImages, styles,
			sourceTrack(base.titleEn()), base.titleEn() != null,
			base.active(), base.showFlag(), base.curationPriority(), base.status(),
			base.requestedAt()));
	}

	@Override
	public List<JobRecord> findJobs(JobQuery query) {
		String sql = "SELECT * FROM place_editorial_jobs WHERE id > :cursor"
			+ (query.status() == null ? "" : " AND status = :status")
			+ " ORDER BY id LIMIT :limit";
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("cursor", query.startAfterId())
			.addValue("limit", query.limit());
		if (query.status() != null) {
			params.addValue("status", query.status().name());
		}
		return namedJdbcTemplate.query(sql, params, this::job);
	}

	@Override
	public Optional<PlaceVisibilityRecord> updateVisibility(VisibilityCommand command) {
		if (command.visible()) {
			jdbcTemplate.update("""
				UPDATE places
				SET active = TRUE, show_flag = TRUE, updated_at = ?
				WHERE id = ?
				""", Timestamp.from(command.updatedAt()), command.placeId());
		} else {
			jdbcTemplate.update("""
				UPDATE places
				SET show_flag = FALSE, updated_at = ?
				WHERE id = ?
				""", Timestamp.from(command.updatedAt()), command.placeId());
		}
		Optional<PlaceVisibilityRecord> updated = jdbcTemplate.query("""
			SELECT id, active, show_flag, updated_at
			FROM places WHERE id = ?
			""", (rs, rowNumber) -> new PlaceVisibilityRecord(
				rs.getLong("id"), rs.getBoolean("active"), rs.getBoolean("show_flag"),
				instant(rs, "updated_at")), command.placeId()).stream().findFirst();
		updated.ifPresent(value -> jdbcTemplate.update("""
			INSERT INTO place_editorial_audits
			    (place_id, job_id, actor_subject, action, details_json, created_at)
			VALUES (?, NULL, ?, 'PLACE_VISIBILITY_UPDATED',
			        JSON_OBJECT('visible', ?, 'active', ?, 'showFlag', ?), ?)
			""", value.placeId(), command.actorSubject(), value.visible(),
			value.active(), value.showFlag(), Timestamp.from(command.updatedAt())));
		return updated;
	}

	@Override
	public Optional<PlacePriorityRecord> updateCurationPriority(PriorityCommand command) {
		int changed = jdbcTemplate.update("""
			UPDATE places SET curation_priority = ?, updated_at = ? WHERE id = ?
			""", command.priority(), Timestamp.from(command.updatedAt()), command.placeId());
		if (changed == 0) {
			return Optional.empty();
		}
		jdbcTemplate.update("""
			INSERT INTO place_editorial_audits
			    (place_id, job_id, actor_subject, action, details_json, created_at)
			VALUES (?, NULL, ?, 'PLACE_PRIORITY_UPDATED',
			        JSON_OBJECT('priority', ?), ?)
			""", command.placeId(), command.actorSubject(), command.priority(),
			Timestamp.from(command.updatedAt()));
		return Optional.of(new PlacePriorityRecord(
			command.placeId(), command.priority(), command.updatedAt()));
	}

	@Override
	public Optional<PlaceImageOrderRecord> reorderImages(ImageOrderCommand command) {
		Integer placeCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM places WHERE id = ?", Integer.class, command.placeId());
		if (placeCount == null || placeCount == 0) {
			return Optional.empty();
		}
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("placeId", command.placeId())
			.addValue("imageIds", command.imageIds());
		Long ownedCount = namedJdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM place_images
			WHERE place_id = :placeId AND id IN (:imageIds)
			""", parameters, Long.class);
		if (ownedCount == null || ownedCount != command.imageIds().size()) {
			throw new IllegalArgumentException("Every image must belong to the place");
		}
		jdbcTemplate.update(
			"UPDATE place_images SET admin_display_order = NULL WHERE place_id = ?",
			command.placeId());
		List<Object[]> orderRows = java.util.stream.IntStream.range(0, command.imageIds().size())
			.mapToObj(index -> new Object[] {
				index + 1, Timestamp.from(command.updatedAt()), command.imageIds().get(index),
				command.placeId()
			})
			.toList();
		jdbcTemplate.batchUpdate("""
			UPDATE place_images
			SET admin_display_order = ?, updated_at = ?
			WHERE id = ? AND place_id = ?
			""", orderRows);
		List<PlaceImageRecord> images = jdbcTemplate.query("""
			SELECT id, image_url, admin_display_order
			FROM place_images
			WHERE place_id = ? AND admin_display_order IS NOT NULL
			ORDER BY admin_display_order
			""", (rs, rowNumber) -> new PlaceImageRecord(
				rs.getLong("id"), rs.getString("image_url"),
				rs.getInt("admin_display_order")), command.placeId());
		jdbcTemplate.update("""
			INSERT INTO place_editorial_audits
			    (place_id, job_id, actor_subject, action, details_json, created_at)
			VALUES (?, NULL, ?, 'PLACE_IMAGES_REORDERED',
			        JSON_OBJECT('imageCount', ?, 'thumbnailImageId', ?), ?)
			""", command.placeId(), command.actorSubject(), images.size(),
			images.getFirst().imageId(), Timestamp.from(command.updatedAt()));
		return Optional.of(new PlaceImageOrderRecord(
			command.placeId(), List.copyOf(images), command.updatedAt()));
	}

	private Optional<Source> findSource(long placeId) {
		return namedJdbcTemplate.query("""
			SELECT p.id,
			""" + SOURCE_FINGERPRINT + """
			AS fingerprint
			FROM places p
			LEFT JOIN place_localizations ko
			  ON ko.place_id = p.id AND ko.language = 'KO'
			LEFT JOIN place_localizations en
			  ON en.place_id = p.id AND en.language = 'EN'
			WHERE p.id = :placeId AND p.active = TRUE
			""", Map.of("placeId", placeId),
			(rs, rowNumber) -> new Source(
				rs.getLong("id"), rs.getString("fingerprint")))
			.stream().findFirst();
	}

	private Optional<EnqueueRecord> findEnqueueByRequestKey(String requestKey) {
		return jdbcTemplate.query("""
			SELECT public_id, place_id, status, priority, trigger_type, requested_at
			FROM place_editorial_jobs WHERE request_key = ?
			""", (rs, rowNumber) -> new EnqueueRecord(
				rs.getString("public_id"),
				rs.getLong("place_id"),
				EditorialJobStatus.valueOf(rs.getString("status")),
				priority(rs.getInt("priority")),
				EditorialTriggerType.valueOf(rs.getString("trigger_type")),
				instant(rs, "requested_at"),
				false), requestKey).stream().findFirst();
	}

	private CandidateRecord candidate(ResultSet rs, int rowNumber) throws SQLException {
			return new CandidateRecord(
			rs.getLong("place_id"), rs.getString("title_ko"), rs.getString("title_en"),
			rs.getString("service_region_code"),
			rs.getString("image_url"), rs.getBoolean("has_ko_overview"),
			rs.getBoolean("queue_eligible"), sourceTrack(rs.getString("title_en")),
			rs.getString("title_en") != null,
			rs.getBoolean("active"), rs.getBoolean("show_flag"),
			rs.getInt("curation_priority"),
			EditorialJobStatus.valueOf(rs.getString("editorial_status")),
			instant(rs, "requested_at"));
	}

	private static EditorialCandidateSourceTrack sourceTrack(String trustedEnglishTitle) {
		return trustedEnglishTitle == null
			? EditorialCandidateSourceTrack.KOREAN_ONLY_AI
			: EditorialCandidateSourceTrack.KTO_BILINGUAL;
	}

	private JobRecord job(ResultSet rs, int rowNumber) throws SQLException {
		return new JobRecord(
			rs.getLong("id"), rs.getString("public_id"), rs.getLong("place_id"),
			EditorialJobStatus.valueOf(rs.getString("status")),
			priority(rs.getInt("priority")),
			EditorialTriggerType.valueOf(rs.getString("trigger_type")),
			rs.getInt("attempt_count"), rs.getString("error_code"),
			rs.getString("error_message"), instant(rs, "requested_at"),
			instant(rs, "started_at"), instant(rs, "completed_at"));
	}

	private static EditorialJobPriority priority(int value) {
		return value >= EditorialJobPriority.HIGH.weight()
			? EditorialJobPriority.HIGH : EditorialJobPriority.NORMAL;
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private record Source(long placeId, String fingerprint) {
	}

	private record ReadyBase(
		long id,
		long placeId,
		String sourceFingerprint,
		String promptVersion,
		String topic,
		String oneLineDescription,
		String shortIntroduction,
		Instant generatedAt
	) {
	}

	private record CandidateDetailBase(
		long placeId,
		String titleKo,
		String titleEn,
		String overviewKo,
		String address,
		String region,
		boolean active,
		boolean showFlag,
		int curationPriority,
		EditorialJobStatus status,
		Instant requestedAt
	) {
	}
}
