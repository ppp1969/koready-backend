package koready_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LocalDevUserSeedIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void localUserSeedIsIdempotent() throws Exception {
		runSeed();
		runSeed();

		Long userId = jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = 'local-user'",
			Long.class);
		assertEquals(1, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM users WHERE public_id = 'local-user'",
			Integer.class));
		assertEquals(1, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_locations WHERE user_id = ? AND deleted_at IS NULL",
			Integer.class,
			userId));
		assertEquals(1, jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM user_locations
			WHERE user_id = ?
			  AND provider = 'KAKAO'
			  AND road_address IS NOT NULL
			  AND latitude IS NOT NULL
			  AND longitude IS NOT NULL
			  AND deleted_at IS NULL
			""",
			Integer.class,
			userId));
		assertEquals(2, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_travel_styles WHERE user_id = ?",
			Integer.class,
			userId));
		assertEquals(jdbcTemplate.queryForObject(
			"SELECT id FROM user_locations WHERE user_id = ? AND deleted_at IS NULL",
			Long.class,
			userId), jdbcTemplate.queryForObject(
			"SELECT default_location_id FROM users WHERE id = ?",
			Long.class,
			userId));
	}

	private void runSeed() throws Exception {
		var resource = new FileSystemResource(Path.of("scripts", "seed-local-user.sql"));
		try (var connection = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(
				connection,
				new EncodedResource(resource, StandardCharsets.UTF_8),
				false,
				false,
				ScriptUtils.DEFAULT_COMMENT_PREFIX,
				ScriptUtils.DEFAULT_STATEMENT_SEPARATOR,
				ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
				ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER);
		}
	}
}
