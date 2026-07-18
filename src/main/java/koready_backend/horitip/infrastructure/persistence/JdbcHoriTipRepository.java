package koready_backend.horitip.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import koready_backend.horitip.application.port.HoriTipRepository;
import koready_backend.horitip.domain.HoriTipDraft;
import koready_backend.horitip.domain.HoriTipPlacement;
import koready_backend.horitip.domain.HoriTipRouteMode;
import koready_backend.horitip.domain.HoriTipScope;
import koready_backend.horitip.domain.HoriTipScopeType;
import koready_backend.horitip.domain.HoriTipStatus;
import koready_backend.horitip.domain.HoriTipTranslation;
import koready_backend.horitip.domain.HoriTipTrigger;
import koready_backend.place.domain.PlaceLanguage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcHoriTipRepository implements HoriTipRepository {

	private static final String TIP_SELECT = """
		SELECT
		    tip.id,
		    tip.code,
		    tip.status,
		    tip.placement,
		    tip.priority,
		    tip.scope_type,
		    tip.segment_modes_json,
		    tip.route_name_contains_json,
		    tip.segment_start_name_contains_json,
		    tip.segment_end_name_contains_json,
		    tip.min_provider_total_time_seconds,
		    tip.min_transfer_count,
		    tip.min_total_walk_distance_meters,
		    tip.valid_from,
		    tip.valid_until,
		    tip.operator_note,
		    tip.version,
		    tip.created_by_subject,
		    tip.updated_by_subject,
		    tip.activated_at,
		    tip.archived_at,
		    tip.created_at,
		    tip.updated_at
		FROM hori_tips tip
		""";

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	public JdbcHoriTipRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public Optional<HoriTipRecord> insertDraft(NewHoriTip tip) {
		try {
			GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
			jdbcTemplate.update(connection -> insertStatement(connection.prepareStatement(
				"""
				INSERT INTO hori_tips
				    (code, source, status, placement, priority, scope_type,
				     segment_modes_json, route_name_contains_json,
				     segment_start_name_contains_json, segment_end_name_contains_json,
				     min_provider_total_time_seconds, min_transfer_count,
				     min_total_walk_distance_meters, valid_from, valid_until, operator_note,
				     version, created_by_subject, updated_by_subject, created_at, updated_at)
				VALUES (?, 'OPERATOR_CURATED', 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
				        1, ?, ?, ?, ?)
				""",
				Statement.RETURN_GENERATED_KEYS), tip), keyHolder);
			Number generatedKey = keyHolder.getKey();
			if (generatedKey == null) {
				throw new IllegalStateException("Hori Tip ID was not generated");
			}
			long id = generatedKey.longValue();
			replaceChildren(id, tip.draft(), tip.now());
			return findById(id);
		} catch (DuplicateKeyException exception) {
			return Optional.empty();
		}
	}

	@Override
	public Optional<HoriTipRecord> findById(long id) {
		return findParent(TIP_SELECT + " WHERE tip.id = ?", id).map(this::hydrate);
	}

	@Override
	public Optional<HoriTipRecord> findByIdForUpdate(long id) {
		return findParent(TIP_SELECT + " WHERE tip.id = ? FOR UPDATE", id).map(this::hydrate);
	}

	@Override
	public List<HoriTipRecord> findPage(ListCriteria criteria) {
		StringBuilder sql = new StringBuilder("SELECT tip.id FROM hori_tips tip WHERE 1 = 1");
		List<Object> parameters = new ArrayList<>();
		if (criteria.status() != null) {
			sql.append(" AND tip.status = ?");
			parameters.add(criteria.status().name());
		}
		if (criteria.code() != null) {
			sql.append(" AND tip.code LIKE CONCAT('%', ?, '%')");
			parameters.add(criteria.code());
		}
		if (criteria.destinationPlaceId() != null) {
			sql.append("""
				 AND EXISTS (
				     SELECT 1
				     FROM hori_tip_destination_places destination
				     WHERE destination.hori_tip_id = tip.id
				       AND destination.place_id = ?
				 )
				""");
			parameters.add(criteria.destinationPlaceId());
		}
		if (criteria.beforeId() != null) {
			sql.append(" AND tip.id < ?");
			parameters.add(criteria.beforeId());
		}
		sql.append(" ORDER BY tip.id DESC LIMIT ?");
		parameters.add(criteria.limit());
		List<Long> ids = jdbcTemplate.query(
			sql.toString(),
			(resultSet, rowNumber) -> resultSet.getLong("id"),
			parameters.toArray());
		return ids.stream().map(id -> findById(id).orElseThrow()).toList();
	}

	@Override
	public Set<Long> findVisiblePlaceIds(List<Long> placeIds) {
		if (placeIds.isEmpty()) {
			return Set.of();
		}
		String placeholders = String.join(",", Collections.nCopies(placeIds.size(), "?"));
		List<Long> rows = jdbcTemplate.query(
			"SELECT id FROM places WHERE active = TRUE AND show_flag = TRUE AND id IN ("
				+ placeholders + ")",
			(resultSet, rowNumber) -> resultSet.getLong("id"),
			placeIds.toArray());
		return Set.copyOf(rows);
	}

	@Override
	public HoriTipRecord updateDraft(
		long id,
		HoriTipDraft draft,
		String actorSubject,
		Instant now
	) {
		int updated = jdbcTemplate.update(
			"""
			UPDATE hori_tips
			SET placement = ?,
			    priority = ?,
			    scope_type = ?,
			    segment_modes_json = ?,
			    route_name_contains_json = ?,
			    segment_start_name_contains_json = ?,
			    segment_end_name_contains_json = ?,
			    min_provider_total_time_seconds = ?,
			    min_transfer_count = ?,
			    min_total_walk_distance_meters = ?,
			    valid_from = ?,
			    valid_until = ?,
			    operator_note = ?,
			    version = version + 1,
			    updated_by_subject = ?,
			    updated_at = ?
			WHERE id = ?
			""",
			draft.placement().name(),
			draft.priority(),
			draft.scope().scopeType().name(),
			json(enumNames(draft.trigger().segmentModes())),
			json(draft.trigger().routeNameContainsAny()),
			json(draft.trigger().segmentStartNameContainsAny()),
			json(draft.trigger().segmentEndNameContainsAny()),
			draft.trigger().minProviderTotalTimeSeconds(),
			draft.trigger().minTransferCount(),
			draft.trigger().minTotalWalkDistanceMeters(),
			timestamp(draft.validFrom()),
			timestamp(draft.validUntil()),
			draft.operatorNote(),
			actorSubject,
			timestamp(now),
			id);
		if (updated != 1) {
			throw new IllegalStateException("Hori Tip draft update did not affect one row");
		}
		replaceChildren(id, draft, now);
		return findById(id).orElseThrow();
	}

	@Override
	public HoriTipRecord updateStatus(
		long id,
		HoriTipStatus status,
		String actorSubject,
		Instant now
	) {
		int updated = jdbcTemplate.update(
			"""
			UPDATE hori_tips
			SET status = ?,
			    version = version + 1,
			    updated_by_subject = ?,
			    updated_at = ?,
			    activated_at = CASE WHEN ? = 'ACTIVE' THEN ? ELSE activated_at END,
			    archived_at = CASE WHEN ? = 'ARCHIVED' THEN ? ELSE archived_at END
			WHERE id = ?
			""",
			status.name(),
			actorSubject,
			timestamp(now),
			status.name(),
			timestamp(now),
			status.name(),
			timestamp(now),
			id);
		if (updated != 1) {
			throw new IllegalStateException("Hori Tip status update did not affect one row");
		}
		return findById(id).orElseThrow();
	}

	@Override
	public void recordAudit(AuditRecord audit) {
		HoriTipRecord resource = audit.after() == null ? audit.before() : audit.after();
		if (resource == null) {
			throw new IllegalArgumentException("A Hori Tip audit requires a resource snapshot");
		}
		jdbcTemplate.update(
			"""
			INSERT INTO hori_tip_audit_logs
			    (hori_tip_id, actor_subject, action, reason,
			     before_status, after_status, before_version, after_version,
			     before_snapshot, after_snapshot, created_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			resource.id(),
			audit.actorSubject(),
			audit.action(),
			audit.reason(),
			statusName(audit.before()),
			statusName(audit.after()),
			version(audit.before()),
			version(audit.after()),
			snapshot(audit.before()),
			snapshot(audit.after()),
			timestamp(audit.createdAt()));
	}

	private PreparedStatement insertStatement(PreparedStatement statement, NewHoriTip tip)
		throws SQLException {
		HoriTipDraft draft = tip.draft();
		statement.setString(1, tip.code());
		statement.setString(2, draft.placement().name());
		statement.setInt(3, draft.priority());
		statement.setString(4, draft.scope().scopeType().name());
		statement.setString(5, json(enumNames(draft.trigger().segmentModes())));
		statement.setString(6, json(draft.trigger().routeNameContainsAny()));
		statement.setString(7, json(draft.trigger().segmentStartNameContainsAny()));
		statement.setString(8, json(draft.trigger().segmentEndNameContainsAny()));
		statement.setObject(9, draft.trigger().minProviderTotalTimeSeconds());
		statement.setObject(10, draft.trigger().minTransferCount());
		statement.setObject(11, draft.trigger().minTotalWalkDistanceMeters());
		statement.setTimestamp(12, timestamp(draft.validFrom()));
		statement.setTimestamp(13, timestamp(draft.validUntil()));
		statement.setString(14, draft.operatorNote());
		statement.setString(15, tip.actorSubject());
		statement.setString(16, tip.actorSubject());
		statement.setTimestamp(17, timestamp(tip.now()));
		statement.setTimestamp(18, timestamp(tip.now()));
		return statement;
	}

	private Optional<ParentRow> findParent(String sql, Object... parameters) {
		return jdbcTemplate.query(sql, this::mapParent, parameters).stream().findFirst();
	}

	private ParentRow mapParent(ResultSet resultSet, int rowNumber) throws SQLException {
		return new ParentRow(
			resultSet.getLong("id"),
			resultSet.getString("code"),
			HoriTipStatus.valueOf(resultSet.getString("status")),
			HoriTipPlacement.valueOf(resultSet.getString("placement")),
			resultSet.getInt("priority"),
			HoriTipScopeType.valueOf(resultSet.getString("scope_type")),
			enumList(resultSet.getString("segment_modes_json")),
			stringList(resultSet.getString("route_name_contains_json")),
			stringList(resultSet.getString("segment_start_name_contains_json")),
			stringList(resultSet.getString("segment_end_name_contains_json")),
			nullableInteger(resultSet, "min_provider_total_time_seconds"),
			nullableInteger(resultSet, "min_transfer_count"),
			nullableInteger(resultSet, "min_total_walk_distance_meters"),
			instant(resultSet, "valid_from"),
			instant(resultSet, "valid_until"),
			resultSet.getString("operator_note"),
			resultSet.getInt("version"),
			resultSet.getString("created_by_subject"),
			resultSet.getString("updated_by_subject"),
			instant(resultSet, "activated_at"),
			instant(resultSet, "archived_at"),
			instant(resultSet, "created_at"),
			instant(resultSet, "updated_at"));
	}

	private HoriTipRecord hydrate(ParentRow row) {
		HoriTipDraft draft = new HoriTipDraft(
			row.placement(),
			row.priority(),
			new HoriTipScope(row.scopeType(), destinations(row.id())),
			new HoriTipTrigger(
				row.segmentModes(),
				row.routeNameContainsAny(),
				row.segmentStartNameContainsAny(),
				row.segmentEndNameContainsAny(),
				row.minProviderTotalTimeSeconds(),
				row.minTransferCount(),
				row.minTotalWalkDistanceMeters()),
			translations(row.id()),
			row.validFrom(),
			row.validUntil(),
			row.operatorNote());
		return new HoriTipRecord(
			row.id(),
			row.code(),
			row.status(),
			draft,
			row.version(),
			row.createdBySubject(),
			row.updatedBySubject(),
			row.activatedAt(),
			row.archivedAt(),
			row.createdAt(),
			row.updatedAt());
	}

	private List<Long> destinations(long horiTipId) {
		return jdbcTemplate.query(
			"""
			SELECT place_id
			FROM hori_tip_destination_places
			WHERE hori_tip_id = ?
			ORDER BY place_id ASC
			""",
			(resultSet, rowNumber) -> resultSet.getLong("place_id"),
			horiTipId);
	}

	private List<HoriTipTranslation> translations(long horiTipId) {
		return jdbcTemplate.query(
			"""
			SELECT language, body
			FROM hori_tip_translations
			WHERE hori_tip_id = ?
			ORDER BY CASE language WHEN 'KO' THEN 0 ELSE 1 END
			""",
			(resultSet, rowNumber) -> new HoriTipTranslation(
				PlaceLanguage.valueOf(resultSet.getString("language")),
				resultSet.getString("body")),
			horiTipId);
	}

	private void replaceChildren(long id, HoriTipDraft draft, Instant now) {
		jdbcTemplate.update(
			"DELETE FROM hori_tip_destination_places WHERE hori_tip_id = ?", id);
		if (!draft.scope().destinationPlaceIds().isEmpty()) {
			jdbcTemplate.batchUpdate(
				"""
				INSERT INTO hori_tip_destination_places (hori_tip_id, place_id, created_at)
				VALUES (?, ?, ?)
				""",
				draft.scope().destinationPlaceIds().stream()
					.map(placeId -> new Object[] {id, placeId, timestamp(now)})
					.toList());
		}
		jdbcTemplate.update("DELETE FROM hori_tip_translations WHERE hori_tip_id = ?", id);
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO hori_tip_translations
			    (hori_tip_id, language, body, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?)
			""",
			draft.translations().stream()
				.map(translation -> new Object[] {
					id,
					translation.language().name(),
					translation.body(),
					timestamp(now),
					timestamp(now)
				})
				.toList());
	}

	private String snapshot(HoriTipRecord record) {
		return record == null ? null : json(record);
	}

	private String json(Object value) {
		try {
			return jsonMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Hori Tip data could not be serialized", exception);
		}
	}

	private List<String> stringList(String value) {
		try {
			return List.of(jsonMapper.readValue(value, String[].class));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Hori Tip data could not be parsed", exception);
		}
	}

	private List<HoriTipRouteMode> enumList(String value) {
		return stringList(value).stream().map(HoriTipRouteMode::valueOf).toList();
	}

	private static List<String> enumNames(List<HoriTipRouteMode> values) {
		return values.stream().map(Enum::name).toList();
	}

	private static String statusName(HoriTipRecord record) {
		return record == null ? null : record.status().name();
	}

	private static Integer version(HoriTipRecord record) {
		return record == null ? null : record.version();
	}

	private static Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp timestamp = resultSet.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
		int value = resultSet.getInt(column);
		return resultSet.wasNull() ? null : value;
	}

	private record ParentRow(
		long id,
		String code,
		HoriTipStatus status,
		HoriTipPlacement placement,
		int priority,
		HoriTipScopeType scopeType,
		List<HoriTipRouteMode> segmentModes,
		List<String> routeNameContainsAny,
		List<String> segmentStartNameContainsAny,
		List<String> segmentEndNameContainsAny,
		Integer minProviderTotalTimeSeconds,
		Integer minTransferCount,
		Integer minTotalWalkDistanceMeters,
		Instant validFrom,
		Instant validUntil,
		String operatorNote,
		int version,
		String createdBySubject,
		String updatedBySubject,
		Instant activatedAt,
		Instant archivedAt,
		Instant createdAt,
		Instant updatedAt
	) {
	}
}
