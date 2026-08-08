package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.kto.application.model.KtoClassificationDecision;
import koready_backend.kto.application.port.KtoClassificationBackfillStore;
import koready_backend.kto.domain.KtoPlaceStyleRuleV1;
import koready_backend.place.domain.TravelStyle;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JdbcKtoClassificationBackfillStoreIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	KtoClassificationBackfillStore store;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clean() {
		jdbcTemplate.update("DELETE FROM tour_api_sync_cursors WHERE api_name = 'INTERNAL_CLASSIFICATION'");
		jdbcTemplate.update("DELETE FROM place_style_mappings");
		jdbcTemplate.update("DELETE FROM places");
	}

	@Test
	void preservesManualMappingsReplacesCurrentRuleMappingsAndAdvancesCheckpointAtomically() {
		long manualPlace = place("manual-place");
		long automaticPlace = place("automatic-place");
		mapping(manualPlace, TravelStyle.DRAMA_LOCATION, "MANUAL", null, true);
		mapping(manualPlace, TravelStyle.NATURE, "AI", KtoPlaceStyleRuleV1.VERSION, false);
		mapping(automaticPlace, TravelStyle.LOCAL_FOOD, "AI", KtoPlaceStyleRuleV1.VERSION, true);

		List<KtoClassificationDecision> decisions = List.of(
			decision(manualPlace, Set.of(TravelStyle.CULTURE_EXPERIENCE), "EX", "EX01"),
			decision(automaticPlace, Set.of(), "XX", "XX01"));

		store.applyPage(KtoPlaceStyleRuleV1.VERSION, decisions, automaticPlace);
		store.applyPage(KtoPlaceStyleRuleV1.VERSION, decisions, automaticPlace);

		assertEquals(2, count("place_style_mappings"));
		assertEquals(1, countWhere("source = 'MANUAL'"));
		assertEquals(1, countWhere("source = 'AI' AND rule_version = 'kto-place-style-v1'"));
		assertEquals(0, countWhere("travel_style IN ('NATURE', 'LOCAL_FOOD')"));
		assertEquals(1, countWhere("travel_style = 'DRAMA_LOCATION' AND is_primary = TRUE"));
		assertEquals(0, countWhere("travel_style = 'CULTURE_EXPERIENCE' AND is_primary = TRUE"));
		assertEquals("KTO_LCLS", jdbcTemplate.queryForObject(
			"SELECT JSON_UNQUOTE(JSON_EXTRACT(evidence_json, '$.origin')) "
				+ "FROM place_style_mappings WHERE source = 'AI'",
			String.class));
		assertEquals(automaticPlace, store.loadCheckpoint(KtoPlaceStyleRuleV1.VERSION));
		store.recordFailure(KtoPlaceStyleRuleV1.VERSION);
		assertEquals(1, jdbcTemplate.queryForObject(
			"SELECT failure_count FROM tour_api_sync_cursors "
				+ "WHERE api_name = 'INTERNAL_CLASSIFICATION'",
			Integer.class));
		assertEquals(automaticPlace, store.loadCheckpoint(KtoPlaceStyleRuleV1.VERSION));

		store.resetCheckpoint(KtoPlaceStyleRuleV1.VERSION);
		assertEquals(0L, store.loadCheckpoint(KtoPlaceStyleRuleV1.VERSION));
	}

	private KtoClassificationDecision decision(
		long placeId,
		Set<TravelStyle> styles,
		String level1,
		String level2
	) {
		return new KtoClassificationDecision(
			placeId,
			"12",
			level1,
			level2,
			level2 + "0100",
			styles);
	}

	private long place(String contentId) {
		jdbcTemplate.update("INSERT INTO places (kto_content_id) VALUES (?)", contentId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			contentId);
	}

	private void mapping(
		long placeId,
		TravelStyle style,
		String source,
		String ruleVersion,
		boolean primary
	) {
		jdbcTemplate.update(
			"INSERT INTO place_style_mappings "
				+ "(place_id, travel_style, source, rule_version, confidence, is_primary) "
				+ "VALUES (?, ?, ?, ?, 1.0000, ?)",
			placeId,
			style.name(),
			source,
			ruleVersion,
			primary);
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private int countWhere(String condition) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM place_style_mappings WHERE " + condition,
			Integer.class);
	}
}
