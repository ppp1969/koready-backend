package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

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
class FestivalAddressServiceRegionMigrationIntegrationTest {

	@Container
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Test
	void backfillsEveryPlaceRegionFromItsAddressWithoutOverwritingExistingValues() {
		Flyway.configure()
			.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
			.locations("classpath:db/migration")
			.target(MigrationVersion.fromVersion("61"))
			.load()
			.migrate();
		JdbcTemplate jdbcTemplate = jdbcTemplate();
		Map<String, String> cases = Map.of(
			"서울특별시 강동구", "SEOUL",
			"인천광역시 부평구", "GYEONGGI",
			"강원특별자치도 강릉시", "GANGWON",
			"충청남도 계룡시", "CHUNGCHEONG",
			"전남광주통합특별시 서구", "JEOLLA",
			"포항시 북구 두호동", "GYEONGSANG",
			"제주특별자치도 제주시", "JEJU");
		cases.forEach((address, expected) -> insertFestival(
			jdbcTemplate, "festival-" + expected, address, null));
		insertFestival(jdbcTemplate, "2755427", null, null);
		insertFestival(jdbcTemplate, "manual", "서울특별시 종로구", "JEJU");
		insertPlace(jdbcTemplate, "ordinary-place", "부산광역시 해운대구", null);

		Flyway.configure()
			.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
			.locations("classpath:db/migration")
			.load()
			.migrate();

		cases.forEach((address, expected) -> assertEquals(
			expected, region(jdbcTemplate, "festival-" + expected), address));
		assertEquals("SEOUL", region(jdbcTemplate, "2755427"));
		assertEquals("JEJU", region(jdbcTemplate, "manual"));
		assertEquals("GYEONGSANG", region(jdbcTemplate, "ordinary-place"));
	}

	private void insertFestival(
		JdbcTemplate jdbcTemplate,
		String contentId,
		String address,
		String serviceRegionCode
	) {
		jdbcTemplate.update(
			"INSERT INTO places (kto_content_id, address, service_region_code) VALUES (?, ?, ?)",
			contentId,
			address,
			serviceRegionCode);
		Long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?", Long.class, contentId);
		jdbcTemplate.update(
			"INSERT INTO place_style_mappings "
				+ "(place_id, travel_style, source, confidence) "
				+ "VALUES (?, 'LOCAL_FESTIVAL', 'MANUAL', 1.0000)",
			placeId);
	}

	private void insertPlace(
		JdbcTemplate jdbcTemplate,
		String contentId,
		String address,
		String serviceRegionCode
	) {
		jdbcTemplate.update(
			"INSERT INTO places (kto_content_id, address, service_region_code) VALUES (?, ?, ?)",
			contentId,
			address,
			serviceRegionCode);
	}

	private String region(JdbcTemplate jdbcTemplate, String contentId) {
		return jdbcTemplate.queryForObject(
			"SELECT service_region_code FROM places WHERE kto_content_id = ?",
			String.class,
			contentId);
	}

	private JdbcTemplate jdbcTemplate() {
		return new JdbcTemplate(new DriverManagerDataSource(
			mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
	}
}
