package koready_backend.kto.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.model.KtoClassificationDecision;
import koready_backend.kto.application.port.KtoClassificationBackfillStore;
import koready_backend.place.domain.TravelStyle;

@Repository
public class JdbcKtoClassificationBackfillStore
	implements KtoClassificationBackfillStore {

	private static final String PROVIDER = "KTO";
	private static final String API_NAME = "INTERNAL_CLASSIFICATION";

	private final JdbcTemplate jdbcTemplate;

	public JdbcKtoClassificationBackfillStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional(readOnly = true)
	public long loadCheckpoint(String ruleVersion) {
		return jdbcTemplate.query(
			"SELECT cursor_value FROM tour_api_sync_cursors "
				+ "WHERE provider = ? AND api_name = ? AND operation = ? "
				+ "AND cursor_type = 'MANUAL'",
			resultSet -> resultSet.next()
				? Long.parseLong(resultSet.getString("cursor_value"))
				: 0L,
			PROVIDER,
			API_NAME,
			ruleVersion);
	}

	@Override
	@Transactional
	public void resetCheckpoint(String ruleVersion) {
		jdbcTemplate.update(
			"DELETE FROM tour_api_sync_cursors "
				+ "WHERE provider = ? AND api_name = ? AND operation = ? "
				+ "AND cursor_type = 'MANUAL'",
			PROVIDER,
			API_NAME,
			ruleVersion);
	}

	@Override
	@Transactional
	public void recordFailure(String ruleVersion) {
		jdbcTemplate.update(
			"""
			INSERT INTO tour_api_sync_cursors
			    (provider, api_name, operation, cursor_type, cursor_value,
			     last_failure_at, failure_count, enabled)
			VALUES (?, ?, ?, 'MANUAL', '0', CURRENT_TIMESTAMP(6), 1, TRUE)
			ON DUPLICATE KEY UPDATE
			    last_failure_at = CURRENT_TIMESTAMP(6),
			    failure_count = failure_count + 1
			""",
			PROVIDER,
			API_NAME,
			ruleVersion);
	}

	@Override
	@Transactional
	public void applyPage(
		String ruleVersion,
		List<KtoClassificationDecision> decisions,
		long lastPlaceId
	) {
		if (decisions == null || decisions.isEmpty()) {
			throw new IllegalArgumentException("Classification page cannot be empty");
		}
		if (lastPlaceId != decisions.getLast().placeId()) {
			throw new IllegalArgumentException("Checkpoint must match the last decision");
		}
		deleteCurrentMappings(ruleVersion, decisions);
		upsertAutomaticMappings(ruleVersion, decisions);
		reselectPrimary(decisions);
		upsertCheckpoint(ruleVersion, lastPlaceId);
	}

	private void deleteCurrentMappings(
		String ruleVersion,
		List<KtoClassificationDecision> decisions
	) {
		String placeholders = placeholders(decisions.size());
		List<Object> parameters = new ArrayList<>();
		parameters.add(ruleVersion);
		decisions.stream().map(KtoClassificationDecision::placeId)
			.forEach(parameters::add);
		jdbcTemplate.update(
			"DELETE FROM place_style_mappings "
				+ "WHERE source = 'AI' AND rule_version = ? "
				+ "AND place_id IN (" + placeholders + ")",
			parameters.toArray());
	}

	private void upsertAutomaticMappings(
		String ruleVersion,
		List<KtoClassificationDecision> decisions
	) {
		List<MappingRow> rows = decisions.stream()
			.flatMap(decision -> decision.styles().stream().sorted()
				.map(style -> new MappingRow(decision, style)))
			.toList();
		if (rows.isEmpty()) {
			return;
		}
		String values = rows.stream()
			.map(ignored -> "(?, ?, 'AI', ?, JSON_OBJECT(" 
				+ "'origin', 'KTO_LCLS', 'ruleVersion', ?, 'travelStyle', ?, "
				+ "'contentTypeId', ?, 'classificationCode1', ?, "
				+ "'classificationCode2', ?, 'classificationCode3', ?), 1.0000, FALSE)")
			.collect(Collectors.joining(","));
		List<Object> parameters = new ArrayList<>(rows.size() * 9);
		for (MappingRow row : rows) {
			KtoClassificationDecision decision = row.decision();
			parameters.add(decision.placeId());
			parameters.add(row.style().name());
			parameters.add(ruleVersion);
			parameters.add(ruleVersion);
			parameters.add(row.style().name());
			parameters.add(decision.contentTypeId());
			parameters.add(decision.classificationCode1());
			parameters.add(decision.classificationCode2());
			parameters.add(decision.classificationCode3());
		}
		jdbcTemplate.update(
			"INSERT INTO place_style_mappings "
				+ "(place_id, travel_style, source, rule_version, evidence_json, confidence, is_primary) "
				+ "VALUES " + values + " ON DUPLICATE KEY UPDATE "
				+ "source = IF(source = 'MANUAL', source, VALUES(source)), "
				+ "rule_version = IF(source = 'MANUAL', rule_version, VALUES(rule_version)), "
				+ "evidence_json = IF(source = 'MANUAL', evidence_json, VALUES(evidence_json)), "
				+ "confidence = IF(source = 'MANUAL', confidence, VALUES(confidence)), "
				+ "is_primary = IF(source = 'MANUAL', is_primary, FALSE)",
			parameters.toArray());
	}

	private void reselectPrimary(List<KtoClassificationDecision> decisions) {
		String placeholders = placeholders(decisions.size());
		Object[] placeIds = decisions.stream()
			.map(KtoClassificationDecision::placeId)
			.toArray();
		jdbcTemplate.update(
			"UPDATE place_style_mappings SET is_primary = FALSE "
				+ "WHERE place_id IN (" + placeholders + ")",
			placeIds);
		jdbcTemplate.update(
			"UPDATE place_style_mappings target JOIN ("
				+ "SELECT place_id, travel_style FROM ("
				+ "SELECT place_id, travel_style, ROW_NUMBER() OVER ("
				+ "PARTITION BY place_id ORDER BY CASE WHEN source = 'MANUAL' THEN 0 ELSE 1 END, "
				+ "confidence DESC, travel_style ASC) AS priority_order "
				+ "FROM place_style_mappings WHERE place_id IN (" + placeholders + ")"
				+ ") ranked WHERE priority_order = 1) selected "
				+ "ON selected.place_id = target.place_id "
				+ "AND selected.travel_style = target.travel_style "
				+ "SET target.is_primary = TRUE",
			placeIds);
	}

	private String placeholders(int count) {
		return String.join(",", java.util.Collections.nCopies(count, "?"));
	}

	private void upsertCheckpoint(String ruleVersion, long lastPlaceId) {
		jdbcTemplate.update(
			"""
			INSERT INTO tour_api_sync_cursors
			    (provider, api_name, operation, cursor_type, cursor_value,
			     last_success_at, failure_count, enabled)
			VALUES (?, ?, ?, 'MANUAL', ?, CURRENT_TIMESTAMP(6), 0, TRUE)
			ON DUPLICATE KEY UPDATE
			    cursor_value = VALUES(cursor_value),
			    last_success_at = CURRENT_TIMESTAMP(6),
			    failure_count = 0,
			    enabled = TRUE
			""",
			PROVIDER,
			API_NAME,
			ruleVersion,
			Long.toString(lastPlaceId));
	}

	private record MappingRow(
		KtoClassificationDecision decision,
		TravelStyle style
	) {
	}
}
