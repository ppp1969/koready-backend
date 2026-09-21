package koready_backend.user.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class SignupLanguageMigrationIntegrationTest {
	@Container
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Test
	void repairsInitialStateAndDefaultWithoutResettingProgressOrAgreements() {
		var config = Flyway.configure()
			.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
			.locations("classpath:db/migration");
		config.target("60").load().migrate();
		var jdbc = new JdbcTemplate(new DriverManagerDataSource(
			mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
		for (String state : List.of("NEED_TERMS", "NEED_LANGUAGE", "NEED_ONBOARDING", "COMPLETED")) {
			jdbc.update("INSERT INTO users (public_id, preferred_language, signup_status) VALUES (?, 'EN', ?)", state, state);
		}
		jdbc.update("INSERT INTO users (public_id, signup_status, deleted_at) VALUES ('deleted', 'NEED_TERMS', NOW(6))");
		jdbc.update("INSERT INTO term_definitions (code, display_order) VALUES ('MIGRATION_TERMS', 1)");
		jdbc.update("""
			INSERT INTO term_versions (term_id, version_label, title, content_url, required, effective_at, published_at)
			SELECT id, '1.0', 'Terms', 'https://example.com/terms', TRUE, NOW(6), NOW(6)
			FROM term_definitions WHERE code = 'MIGRATION_TERMS'
			""");
		jdbc.update("""
			INSERT INTO user_term_agreements (user_id, term_version_id, agreed, agreed_at, created_at, updated_at)
			SELECT u.id, v.id, TRUE, NOW(6), NOW(6), NOW(6)
			FROM users u CROSS JOIN term_versions v WHERE u.public_id = 'NEED_LANGUAGE'
			""");
		var agreements = jdbc.queryForList("SELECT * FROM user_term_agreements");
		config.target("61").load().migrate();
		jdbc.update("INSERT INTO users (public_id) VALUES ('new-default')");
		for (String id : List.of("NEED_TERMS", "NEED_LANGUAGE", "new-default")) {
			assertEquals("NEED_LANGUAGE", jdbc.queryForObject(
				"SELECT signup_status FROM users WHERE public_id = ?", String.class, id));
		}
		for (String state : List.of("NEED_ONBOARDING", "COMPLETED")) {
			assertEquals(state, jdbc.queryForObject(
				"SELECT signup_status FROM users WHERE public_id = ?", String.class, state));
		}
		assertEquals("EN", jdbc.queryForObject("SELECT preferred_language FROM users WHERE public_id = 'NEED_TERMS'", String.class));
		assertEquals("NEED_TERMS", jdbc.queryForObject("SELECT signup_status FROM users WHERE public_id = 'deleted'", String.class));
		assertEquals(agreements, jdbc.queryForList("SELECT * FROM user_term_agreements"));
	}
}
