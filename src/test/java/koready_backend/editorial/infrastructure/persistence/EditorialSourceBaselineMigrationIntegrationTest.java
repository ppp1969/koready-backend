package koready_backend.editorial.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class EditorialSourceBaselineMigrationIntegrationTest {

	@Container
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Test
	void baselinesExistingReadyContentWithoutQueueingAiWork() {
		migrateTo("58");
		JdbcTemplate jdbc = jdbcTemplate();
		jdbc.update("""
			INSERT INTO places
			    (kto_content_id, active, show_flag, road_address, first_image_url)
			VALUES ('baseline-place', TRUE, TRUE, 'Seoul', 'https://example.com/image.jpg')
			""");
		long placeId = jdbc.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = 'baseline-place'", Long.class);
		jdbc.update("""
			INSERT INTO place_localizations
			    (place_id, language, title, overview, address_text,
			     translation_source, source_hash)
			VALUES (?, 'KO', 'Test', '<p>Overview</p>', 'Seoul', 'KTO_KO', ?)
			""", placeId, "f".repeat(64));
		jdbc.update("""
			INSERT INTO place_style_mappings
			    (place_id, travel_style, source, confidence, rule_version, is_primary)
			VALUES (?, 'CULTURE_EXPERIENCE', 'LCLS', 1.0, 'rule-v1', TRUE)
			""", placeId);
		jdbc.update("""
			INSERT INTO place_editorial_contents
			    (place_id, source_fingerprint, prompt_version, status, generated_at)
			VALUES (?, ?, 'prompt-v1', 'READY', CURRENT_TIMESTAMP(6))
			""", placeId, "a".repeat(64));

		migrateToLatest();

		String snapshot = jdbc.queryForObject("""
			SELECT source_snapshot_json FROM place_editorial_contents WHERE place_id = ?
			""", String.class, placeId);
		assertNotNull(snapshot);
		assertEquals("Overview", jdbc.queryForObject("""
			SELECT JSON_UNQUOTE(JSON_EXTRACT(source_snapshot_json, '$.overviewKo'))
			FROM place_editorial_contents WHERE place_id = ?
			""", String.class, placeId));
		assertEquals(1, jdbc.queryForObject("""
			SELECT source_fingerprint = SHA2(
			    CONCAT_WS('|', 'Test', '', 'Seoul', 'Overview', 'CULTURE_EXPERIENCE', ''), 256)
			FROM place_editorial_contents WHERE place_id = ?
			""", Integer.class, placeId));
		assertEquals(0, jdbc.queryForObject(
			"SELECT COUNT(*) FROM place_editorial_jobs", Integer.class));
	}

	private void migrateTo(String version) {
		flyway().target(MigrationVersion.fromVersion(version)).load().migrate();
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
			mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
	}
}
