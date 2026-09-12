package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
class CuratedEnglishAddressMigrationIntegrationTest {

	@Container
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Test
	void normalizesOnlyDuplicatedKoreanAddressesForApprovedKtoPlaces() {
		migrateTo("53");
		JdbcTemplate jdbcTemplate = jdbcTemplate();
		long brokenPlaceId = insertPlace(jdbcTemplate, "132183");
		insertLocalizations(
			jdbcTemplate,
			brokenPlaceId,
			"광장시장",
			"Gwangjang Market",
			"서울특별시 종로구 창경궁로 88");
		long manuallyCorrectedPlaceId = insertPlace(jdbcTemplate, "126508");
		insertLocalizations(
			jdbcTemplate,
			manuallyCorrectedPlaceId,
			"경복궁",
			"Gyeongbokgung Palace",
			"161 Sajik-ro, Jongno-gu, Seoul");
		long unrelatedPlaceId = insertPlace(jdbcTemplate, "not-approved");
		insertLocalizations(
			jdbcTemplate,
			unrelatedPlaceId,
			"일반 장소",
			"Other Place",
			"서울특별시 종로구 창경궁로 88");

		migrateToLatest();

		assertEquals(
			"88, Changgyeonggung-ro, Jongno-gu, Seoul",
			address(jdbcTemplate, brokenPlaceId));
		assertEquals(
			"161 Sajik-ro, Jongno-gu, Seoul",
			address(jdbcTemplate, manuallyCorrectedPlaceId));
		assertEquals(
			"서울특별시 종로구 창경궁로 88",
			address(jdbcTemplate, unrelatedPlaceId));
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

	private void insertLocalizations(
		JdbcTemplate jdbcTemplate,
		long placeId,
		String titleKo,
		String titleEn,
		String addressEn
	) {
		String addressKo = "서울특별시 종로구 창경궁로 88";
		jdbcTemplate.update(
			"INSERT INTO place_localizations "
				+ "(place_id, language, title, address_text, translation_source) "
				+ "VALUES (?, 'KO', ?, ?, 'KTO_KO')",
			placeId,
			titleKo,
			addressKo);
		jdbcTemplate.update(
			"INSERT INTO place_localizations "
				+ "(place_id, language, title, address_text, translation_source) "
				+ "VALUES (?, 'EN', ?, ?, 'MANUAL_EDITED')",
			placeId,
			titleEn,
			addressEn);
	}

	private String address(JdbcTemplate jdbcTemplate, long placeId) {
		return jdbcTemplate.queryForObject(
			"SELECT address_text FROM place_localizations "
				+ "WHERE place_id = ? AND language = 'EN'",
			String.class,
			placeId);
	}
}
