package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
class MissingPlaceServiceRegionMigrationIntegrationTest {

	@Container
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Test
	void backfillsOnlyMissingRegionsFromAreaOrLegalDongCodes() {
		migrateTo("56");
		JdbcTemplate jdbcTemplate = jdbcTemplate();
		long gangneungFestival = insertPlace(jdbcTemplate, "festival-4", null, null, "51");
		long seoulPlace = insertPlace(jdbcTemplate, "seoul", null, "1", null);
		long manuallyAssigned = insertPlace(
			jdbcTemplate, "manual", "JEJU", null, "51");
		long unknown = insertPlace(jdbcTemplate, "unknown", null, null, "99");

		migrateToLatest();

		assertEquals("GANGWON", region(jdbcTemplate, gangneungFestival));
		assertEquals("SEOUL", region(jdbcTemplate, seoulPlace));
		assertEquals("JEJU", region(jdbcTemplate, manuallyAssigned));
		assertNull(region(jdbcTemplate, unknown));
	}

	private long insertPlace(
		JdbcTemplate jdbcTemplate,
		String contentId,
		String serviceRegionCode,
		String areaCode,
		String legalDongRegionCode
	) {
		jdbcTemplate.update(
			"INSERT INTO places "
				+ "(kto_content_id, service_region_code, area_code, ldong_regn_cd) "
				+ "VALUES (?, ?, ?, ?)",
			contentId,
			serviceRegionCode,
			areaCode,
			legalDongRegionCode);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			contentId);
	}

	private String region(JdbcTemplate jdbcTemplate, long placeId) {
		return jdbcTemplate.queryForObject(
			"SELECT service_region_code FROM places WHERE id = ?",
			String.class,
			placeId);
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
