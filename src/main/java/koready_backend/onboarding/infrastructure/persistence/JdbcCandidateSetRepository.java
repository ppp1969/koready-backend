package koready_backend.onboarding.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import koready_backend.onboarding.application.port.CandidateSetRepository;
import koready_backend.onboarding.domain.CandidatePlaceReadiness;
import koready_backend.onboarding.domain.CandidateSetDraft;
import koready_backend.onboarding.domain.CandidateSetItemDraft;
import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcCandidateSetRepository implements CandidateSetRepository {

	private static final String SET_SELECT = """
		SELECT
		    candidate_set.id,
		    candidate_set.public_id,
		    candidate_set.title,
		    candidate_set.version,
		    candidate_set.status,
		    candidate_set.published_at,
		    candidate_set.published_by_subject,
		    candidate_set.archived_at,
		    candidate_set.created_at,
		    candidate_set.updated_at,
		    EXISTS (
		        SELECT 1
		        FROM onboarding_candidate_current current_set
		        WHERE current_set.candidate_set_id = candidate_set.id
		    ) AS is_current,
		    (
		        SELECT COUNT(*)
		        FROM onboarding_candidate_set_items item_count
		        WHERE item_count.candidate_set_id = candidate_set.id
		    ) AS item_count
		FROM onboarding_candidate_sets candidate_set
		""";

	private static final String ITEM_SELECT = """
		SELECT
		    item.place_id,
		    item.display_order,
		    item.representative_image_id,
		    item.curator_message_ko,
		    item.curator_message_en,
		    item.display_tags_json,
		    item.editor_note,
		    korean.title AS title_ko,
		    english.title AS title_en,
		    place.first_image_url,
		    place.service_region_code,
		    region.name_ko AS service_region_name_ko,
		    region.name_en AS service_region_name_en,
		    style.travel_style,
		    place.active,
		    place.show_flag,
		    COALESCE(place.road_address, place.address, korean.address_text) AS address_text,
		    place.latitude,
		    place.longitude
		FROM onboarding_candidate_set_items item
		JOIN places place ON place.id = item.place_id
		LEFT JOIN place_localizations korean
		    ON korean.place_id = place.id AND korean.language = 'KO'
		LEFT JOIN place_localizations english
		    ON english.place_id = place.id AND english.language = 'EN'
		LEFT JOIN service_regions region ON region.code = place.service_region_code
		LEFT JOIN place_style_mappings style
		    ON style.place_id = place.id
		   AND style.travel_style = (
		    SELECT selected_style.travel_style
		    FROM place_style_mappings selected_style
		    WHERE selected_style.place_id = place.id
		    ORDER BY selected_style.confidence DESC, selected_style.travel_style ASC
		    LIMIT 1
		)
		WHERE item.candidate_set_id = ?
		ORDER BY item.display_order ASC
		""";

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	public JdbcCandidateSetRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public CandidateSetRecord insertDraft(String publicId, String title, Instant now) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(
				"""
				INSERT INTO onboarding_candidate_sets
				    (public_id, title, version, status, created_at, updated_at)
				VALUES (?, ?, NULL, 'DRAFT', ?, ?)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, publicId);
			statement.setString(2, title);
			statement.setTimestamp(3, timestamp(now));
			statement.setTimestamp(4, timestamp(now));
			return statement;
		}, keyHolder);
		Number generatedKey = keyHolder.getKey();
		if (generatedKey == null) {
			throw new IllegalStateException("Candidate set ID was not generated");
		}
		long id = generatedKey.longValue();
		jdbcTemplate.update(
			"UPDATE onboarding_candidate_sets SET version = ? WHERE id = ?",
			id,
			id);
		return findByPublicId(publicId).orElseThrow();
	}

	@Override
	public Optional<CandidateSetRecord> findByPublicId(String publicId) {
		return jdbcTemplate.query(
			SET_SELECT + " WHERE candidate_set.public_id = ?",
			this::mapSet,
			publicId).stream().findFirst();
	}

	@Override
	public Optional<CandidateSetRecord> findByPublicIdForUpdate(String publicId) {
		return jdbcTemplate.query(
			SET_SELECT + " WHERE candidate_set.public_id = ? FOR UPDATE",
			this::mapSet,
			publicId).stream().findFirst();
	}

	@Override
	public Optional<CandidateSetRecord> findCurrent() {
		return jdbcTemplate.query(
			SET_SELECT + """
			 JOIN onboarding_candidate_current current_set
			   ON current_set.candidate_set_id = candidate_set.id
			 WHERE current_set.slot = 1
			   AND candidate_set.status = 'PUBLISHED'
			 """,
			this::mapSet).stream().findFirst();
	}

	@Override
	public List<CandidateSetRecord> findPage(
		CandidateSetStatus status,
		Long beforeId,
		int limit
	) {
		StringBuilder sql = new StringBuilder(SET_SELECT).append(" WHERE 1 = 1");
		List<Object> parameters = new ArrayList<>();
		if (status != null) {
			sql.append(" AND candidate_set.status = ?");
			parameters.add(status.name());
		}
		if (beforeId != null) {
			sql.append(" AND candidate_set.id < ?");
			parameters.add(beforeId);
		}
		sql.append(" ORDER BY candidate_set.id DESC LIMIT ?");
		parameters.add(limit);
		return jdbcTemplate.query(sql.toString(), this::mapSet, parameters.toArray());
	}

	@Override
	public List<CandidateItemRecord> findItems(long candidateSetId) {
		return jdbcTemplate.query(ITEM_SELECT, this::mapItem, candidateSetId);
	}

	@Override
	public void replaceDraft(long candidateSetId, CandidateSetDraft draft, Instant now) {
		int updated = jdbcTemplate.update(
			"""
			UPDATE onboarding_candidate_sets
			SET title = ?, updated_at = ?
			WHERE id = ? AND status = 'DRAFT'
			""",
			draft.title(),
			timestamp(now),
			candidateSetId);
		if (updated != 1) {
			throw new IllegalStateException("Candidate set is no longer editable");
		}
		jdbcTemplate.update(
			"DELETE FROM onboarding_candidate_set_items WHERE candidate_set_id = ?",
			candidateSetId);
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO onboarding_candidate_set_items
			    (candidate_set_id, place_id, display_order, representative_image_id,
			     curator_message_ko, curator_message_en, display_tags_json, editor_note,
			     created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			draft.items().stream()
				.map(item -> new Object[] {
					candidateSetId,
					item.placeId(),
					item.displayOrder(),
					item.representativeImageId(),
					item.curatorMessageKo(),
					item.curatorMessageEn(),
					json(item.displayTags()),
					item.editorNote(),
					timestamp(now),
					timestamp(now)
				})
				.toList());
	}

	@Override
	public Map<Long, CandidatePlaceReadiness> findPlaceReadiness(
		List<CandidateSetItemDraft> items
	) {
		if (items.isEmpty()) {
			return Map.of();
		}
		String placeholders = String.join(",", java.util.Collections.nCopies(items.size(), "?"));
		String sql = """
			SELECT
			    place.id,
			    place.active,
			    place.show_flag,
			    korean.title AS title_ko,
			    COALESCE(place.road_address, place.address, korean.address_text) AS address_text,
			    place.latitude,
			    place.longitude,
			    place.first_image_url,
			    place.service_region_code
			FROM places place
			LEFT JOIN place_localizations korean
			    ON korean.place_id = place.id AND korean.language = 'KO'
			WHERE place.id IN (%s)
			""".formatted(placeholders);
		Map<Long, PlaceRow> places = new HashMap<>();
		jdbcTemplate.query(
			sql,
			resultSet -> {
				PlaceRow row = mapPlace(resultSet);
				places.put(row.placeId(), row);
			},
			items.stream().map(CandidateSetItemDraft::placeId).toArray());

		Map<Long, CandidatePlaceReadiness> result = new LinkedHashMap<>();
		for (CandidateSetItemDraft item : items) {
			result.put(item.placeId(), readiness(
				item.placeId(),
				places.get(item.placeId()),
				item.representativeImageId()));
		}
		return result;
	}

	@Override
	public boolean markPublished(long candidateSetId, String actorSubject, Instant now) {
		return jdbcTemplate.update(
			"""
			UPDATE onboarding_candidate_sets
			SET status = 'PUBLISHED',
			    published_by_subject = ?,
			    published_at = ?,
			    updated_at = ?
			WHERE id = ? AND status = 'DRAFT'
			""",
			actorSubject,
			timestamp(now),
			timestamp(now),
			candidateSetId) == 1;
	}

	@Override
	public void replaceCurrent(long candidateSetId, Instant now) {
		jdbcTemplate.update(
			"""
			INSERT INTO onboarding_candidate_current (slot, candidate_set_id, updated_at)
			VALUES (1, ?, ?)
			ON DUPLICATE KEY UPDATE
			    candidate_set_id = VALUES(candidate_set_id),
			    updated_at = VALUES(updated_at)
			""",
			candidateSetId,
			timestamp(now));
	}

	@Override
	public boolean markArchived(long candidateSetId, String actorSubject, Instant now) {
		return jdbcTemplate.update(
			"""
			UPDATE onboarding_candidate_sets
			SET status = 'ARCHIVED',
			    archived_by_subject = ?,
			    archived_at = ?,
			    updated_at = ?
			WHERE id = ? AND status IN ('DRAFT', 'PUBLISHED')
			""",
			actorSubject,
			timestamp(now),
			timestamp(now),
			candidateSetId) == 1;
	}

	@Override
	public void clearCurrent(long candidateSetId) {
		jdbcTemplate.update(
			"DELETE FROM onboarding_candidate_current WHERE candidate_set_id = ?",
			candidateSetId);
	}

	@Override
	public void recordAudit(AuditRecord audit) {
		jdbcTemplate.update(
			"""
			INSERT INTO admin_audit_logs
			    (actor_subject, action, resource_type, resource_id,
			     before_status, after_status, created_at)
			VALUES (?, ?, 'ONBOARDING_CANDIDATE_SET', ?, ?, ?, ?)
			""",
			audit.actorSubject(),
			audit.action(),
			audit.resourceId(),
			audit.beforeStatus() == null ? null : audit.beforeStatus().name(),
			audit.afterStatus() == null ? null : audit.afterStatus().name(),
			timestamp(audit.createdAt()));
	}

	private CandidateSetRecord mapSet(ResultSet resultSet, int rowNumber) throws SQLException {
		return new CandidateSetRecord(
			resultSet.getLong("id"),
			resultSet.getString("public_id"),
			resultSet.getString("title"),
			Math.toIntExact(resultSet.getLong("version")),
			CandidateSetStatus.valueOf(resultSet.getString("status")),
			instant(resultSet, "published_at"),
			resultSet.getString("published_by_subject"),
			instant(resultSet, "archived_at"),
			instant(resultSet, "created_at"),
			instant(resultSet, "updated_at"),
			resultSet.getBoolean("is_current"),
			resultSet.getInt("item_count"));
	}

	private CandidateItemRecord mapItem(ResultSet resultSet, int rowNumber) throws SQLException {
		long placeId = resultSet.getLong("place_id");
		Long representativeImageId = nullableLong(resultSet, "representative_image_id");
		PlaceRow place = new PlaceRow(
			placeId,
			resultSet.getBoolean("active"),
			resultSet.getBoolean("show_flag"),
			resultSet.getString("title_ko"),
			resultSet.getString("address_text"),
			resultSet.getObject("latitude"),
			resultSet.getObject("longitude"),
			resultSet.getString("first_image_url"),
			resultSet.getString("service_region_code"));
		String regionCode = resultSet.getString("service_region_code");
		String travelStyle = resultSet.getString("travel_style");
		return new CandidateItemRecord(
			placeId,
			resultSet.getInt("display_order"),
			representativeImageId,
			resultSet.getString("curator_message_ko"),
			resultSet.getString("curator_message_en"),
			stringList(resultSet.getString("display_tags_json")),
			resultSet.getString("editor_note"),
			resultSet.getString("title_ko"),
			resultSet.getString("title_en"),
			resultSet.getString("first_image_url"),
			regionCode == null ? null : ServiceRegionCode.valueOf(regionCode),
			resultSet.getString("service_region_name_ko"),
			resultSet.getString("service_region_name_en"),
			travelStyle == null ? null : TravelStyle.valueOf(travelStyle),
			readiness(placeId, place, representativeImageId));
	}

	private static PlaceRow mapPlace(ResultSet resultSet) throws SQLException {
		return new PlaceRow(
			resultSet.getLong("id"),
			resultSet.getBoolean("active"),
			resultSet.getBoolean("show_flag"),
			resultSet.getString("title_ko"),
			resultSet.getString("address_text"),
			resultSet.getObject("latitude"),
			resultSet.getObject("longitude"),
			resultSet.getString("first_image_url"),
			resultSet.getString("service_region_code"));
	}

	private static CandidatePlaceReadiness readiness(
		long placeId,
		PlaceRow place,
		Long representativeImageId
	) {
		List<String> reasons = new ArrayList<>();
		if (place == null) {
			reasons.add("INACTIVE");
			return CandidatePlaceReadiness.notReady(placeId, reasons);
		}
		if (!place.active() || !place.showFlag()) {
			reasons.add("INACTIVE");
		}
		if (isBlank(place.titleKo())) {
			reasons.add("MISSING_TITLE_KO");
		}
		if (isBlank(place.address())) {
			reasons.add("MISSING_ADDRESS");
		}
		if (place.latitude() == null || place.longitude() == null) {
			reasons.add("MISSING_COORDINATES");
		}
		if (isBlank(place.imageUrl())) {
			reasons.add("MISSING_IMAGE");
		}
		if (representativeImageId != null) {
			reasons.add("IMAGE_NOT_OWNED_BY_PLACE");
		}
		if (isBlank(place.serviceRegionCode())) {
			reasons.add("MISSING_SERVICE_REGION");
		}
		return reasons.isEmpty()
			? CandidatePlaceReadiness.ready(placeId)
			: CandidatePlaceReadiness.notReady(placeId, reasons);
	}

	private String json(Object value) {
		try {
			return jsonMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Candidate tags could not be serialized", exception);
		}
	}

	private List<String> stringList(String value) {
		try {
			return List.of(jsonMapper.readValue(value, String[].class));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Candidate tags could not be parsed", exception);
		}
	}

	private static Timestamp timestamp(Instant instant) {
		return Timestamp.from(instant);
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp timestamp = resultSet.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private record PlaceRow(
		long placeId,
		boolean active,
		boolean showFlag,
		String titleKo,
		String address,
		Object latitude,
		Object longitude,
		String imageUrl,
		String serviceRegionCode
	) {
	}
}
