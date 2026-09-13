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
class NestedKoreanPlaceAliasMigrationIntegrationTest {

	@Container
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Test
	void removesOnlyTrailingKoreanAliasesIncludingNestedParentheses() {
		migrateTo("54");
		JdbcTemplate jdbcTemplate = jdbcTemplate();
		long simpleAlias = insertEnglishTitle(jdbcTemplate, "title-simple", "Gwangjang Market (광장시장)");
		long nestedAlias = insertEnglishTitle(
			jdbcTemplate,
			"title-nested",
			"Sweet Park (Lotte Children's Food Experience Center) "
				+ "(스위트파크(롯데어린이식품체험관))");
		long spacedNestedAlias = insertEnglishTitle(
			jdbcTemplate,
			"title-spaced-nested",
			"Daedunsan Provincial Park (Geumsan Section) (대둔산도립공원 (금산))");
		long englishParentheses = insertEnglishTitle(
			jdbcTemplate,
			"title-english-parentheses",
			"Museum (Seoul (Main Hall))");
		long nonTrailingKorean = insertEnglishTitle(
			jdbcTemplate,
			"title-non-trailing",
			"Museum (서울관) Main Hall");
		long unclosedKoreanAlias = insertEnglishTitle(
			jdbcTemplate,
			"title-unclosed-korean",
			"Sehwa Fifth-day Market (세화민속오일시장");
		long unclosedEnglishParentheses = insertEnglishTitle(
			jdbcTemplate,
			"title-unclosed-english",
			"Museum (Seoul Main Hall");

		migrateToLatest();

		assertEquals("Gwangjang Market", title(jdbcTemplate, simpleAlias));
		assertEquals(
			"Sweet Park (Lotte Children's Food Experience Center)",
			title(jdbcTemplate, nestedAlias));
		assertEquals(
			"Daedunsan Provincial Park (Geumsan Section)",
			title(jdbcTemplate, spacedNestedAlias));
		assertEquals("Museum (Seoul (Main Hall))", title(jdbcTemplate, englishParentheses));
		assertEquals("Museum (서울관) Main Hall", title(jdbcTemplate, nonTrailingKorean));
		assertEquals("Sehwa Fifth-day Market", title(jdbcTemplate, unclosedKoreanAlias));
		assertEquals("Museum (Seoul Main Hall", title(jdbcTemplate, unclosedEnglishParentheses));
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

	private long insertEnglishTitle(JdbcTemplate jdbcTemplate, String contentId, String title) {
		jdbcTemplate.update(
			"INSERT INTO places "
				+ "(kto_content_id, service_region_code, show_flag, active) "
				+ "VALUES (?, 'SEOUL', TRUE, TRUE)",
			contentId);
		long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			contentId);
		jdbcTemplate.update(
			"INSERT INTO place_localizations "
				+ "(place_id, language, title, translation_source) "
				+ "VALUES (?, 'EN', ?, 'KTO_EN')",
			placeId,
			title);
		return placeId;
	}

	private String title(JdbcTemplate jdbcTemplate, long placeId) {
		return jdbcTemplate.queryForObject(
			"SELECT title FROM place_localizations WHERE place_id = ? AND language = 'EN'",
			String.class,
			placeId);
	}
}
