package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PlaceStyleMappingMigrationIntegrationTest {

	@Container
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Test
	void preservesExistingMappingsAndSelectsManualStyleAsPrimary() {
		migrateTo("39");
		JdbcTemplate jdbcTemplate = jdbcTemplate();
		long placeId = insertPlace(jdbcTemplate, "classification-migration-preserve");
		insertStyle(jdbcTemplate, placeId, "NATURE", "AI", "1.0000");
		insertStyle(jdbcTemplate, placeId, "LOCAL_FOOD", "MANUAL", "0.5000");

		migrateToLatest();

		assertEquals(2, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM place_style_mappings WHERE place_id = ?",
			Integer.class,
			placeId));
		assertEquals("LOCAL_FOOD", jdbcTemplate.queryForObject(
			"SELECT travel_style FROM place_style_mappings "
				+ "WHERE place_id = ? AND is_primary = TRUE",
			String.class,
			placeId));

		jdbcTemplate.update(
			"UPDATE place_style_mappings SET rule_version = ?, evidence_json = ? "
				+ "WHERE place_id = ? AND travel_style = 'NATURE'",
			"kto-place-style-v1",
			"{\"level1\":\"NA\"}",
			placeId);
		assertEquals("kto-place-style-v1", jdbcTemplate.queryForObject(
			"SELECT rule_version FROM place_style_mappings "
				+ "WHERE place_id = ? AND travel_style = 'NATURE'",
			String.class,
			placeId));

		long constrainedPlaceId = insertPlace(
			jdbcTemplate,
			"classification-migration-unique");
		insertStyle(jdbcTemplate, constrainedPlaceId, "LOCAL_FOOD", "LCLS", "0.9000");
		insertStyle(jdbcTemplate, constrainedPlaceId, "NATURE", "AI", "0.8000");
		jdbcTemplate.update(
			"UPDATE place_style_mappings SET is_primary = TRUE "
				+ "WHERE place_id = ? AND travel_style = 'LOCAL_FOOD'",
			constrainedPlaceId);

		assertThrows(
			DataAccessException.class,
			() -> jdbcTemplate.update(
				"UPDATE place_style_mappings SET is_primary = TRUE "
					+ "WHERE place_id = ? AND travel_style = 'NATURE'",
				constrainedPlaceId));
		assertThrows(
			DataAccessException.class,
			() -> jdbcTemplate.update(
				"UPDATE place_style_mappings SET rule_version = ' ' "
					+ "WHERE place_id = ? AND travel_style = 'NATURE'",
				constrainedPlaceId));
		assertThrows(
			DataAccessException.class,
			() -> jdbcTemplate.update(
				"UPDATE place_style_mappings SET evidence_json = JSON_ARRAY('NA') "
					+ "WHERE place_id = ? AND travel_style = 'NATURE'",
				constrainedPlaceId));
	}

	private void migrateTo(String version) {
		flyway()
			.target(MigrationVersion.fromVersion(version))
			.load()
			.migrate();
	}

	private void migrateToLatest() {
		flyway().load().migrate();
	}

	private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
		return Flyway.configure()
			.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
			.locations("classpath:db/migration");
	}

	private JdbcTemplate jdbcTemplate() {
		return new JdbcTemplate(new DriverManagerDataSource(
			mysql.getJdbcUrl(),
			mysql.getUsername(),
			mysql.getPassword()));
	}

	private long insertPlace(JdbcTemplate jdbcTemplate, String contentId) {
		jdbcTemplate.update(
			"INSERT INTO places "
				+ "(kto_content_id, service_region_code, show_flag, active) "
				+ "VALUES (?, 'SEOUL', TRUE, TRUE)",
			contentId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			contentId);
	}

	private void insertStyle(
		JdbcTemplate jdbcTemplate,
		long placeId,
		String travelStyle,
		String source,
		String confidence
	) {
		jdbcTemplate.update(
			"INSERT INTO place_style_mappings "
				+ "(place_id, travel_style, source, confidence) VALUES (?, ?, ?, ?)",
			placeId,
			travelStyle,
			source,
			confidence);
	}
}
