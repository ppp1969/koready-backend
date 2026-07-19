package koready_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
class LocalDevBuddySeedIntegrationTest {

	private static final Path SEED = Path.of("scripts", "seed-local-buddy.sql");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void localBuddySeedIsIdempotentAndRestoresADeterministicDemoProfile()
		throws Exception {
		runSeed();
		long demoUserId = userId("local-buddy-demo");
		long profileId = profileId(demoUserId);
		long viewerUserId = insertViewer();
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_profile_styles (profile_id, buddy_style, display_order)
			VALUES (?, 'QUIET_TRAVEL', 3)
			""",
			profileId);
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_social_links
			    (profile_id, link_type, link_value, display_order)
			VALUES (?, 'THREADS', '@stale_demo', 2)
			""",
			profileId);
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_blocks (blocker_user_id, blocked_user_id, created_at)
			VALUES (?, ?, NOW(6)), (?, ?, NOW(6))
			""",
			viewerUserId,
			demoUserId,
			demoUserId,
			viewerUserId);

		runSeed();

		assertEquals(1, count("users", "public_id", "local-buddy-demo"));
		assertEquals(1, count("buddy_profiles", "user_id", demoUserId));
		assertEquals("EN", jdbcTemplate.queryForObject(
			"SELECT preferred_language FROM users WHERE id = ?",
			String.class,
			demoUserId));
		assertEquals("COMPLETED", jdbcTemplate.queryForObject(
			"SELECT signup_status FROM users WHERE id = ?",
			String.class,
			demoUserId));
		assertNull(jdbcTemplate.queryForObject(
			"SELECT profile_image_url FROM buddy_profiles WHERE id = ?",
			String.class,
			profileId));
		assertEquals("KoReady Demo Buddy", jdbcTemplate.queryForObject(
			"SELECT nickname FROM buddy_profiles WHERE id = ?",
			String.class,
			profileId));
		assertEquals("INTERMEDIATE", jdbcTemplate.queryForObject(
			"SELECT korean_level FROM buddy_profiles WHERE id = ?",
			String.class,
			profileId));
		assertTrue(jdbcTemplate.queryForObject(
			"SELECT profile_public FROM buddy_profiles WHERE id = ?",
			Boolean.class,
			profileId));
		assertTrue(jdbcTemplate.queryForObject(
			"SELECT sns_public FROM buddy_profiles WHERE id = ?",
			Boolean.class,
			profileId));
		assertTrue(jdbcTemplate.queryForObject(
			"SELECT allows_messages FROM buddy_profiles WHERE id = ?",
			Boolean.class,
			profileId));
		assertEquals(List.of("EN", "KO"), jdbcTemplate.queryForList(
			"""
			SELECT language_code FROM buddy_profile_languages
			WHERE profile_id = ? ORDER BY display_order
			""",
			String.class,
			profileId));
		assertEquals(List.of("FOODIE", "PHOTOGRAPHY"), jdbcTemplate.queryForList(
			"""
			SELECT buddy_style FROM buddy_profile_styles
			WHERE profile_id = ? ORDER BY display_order
			""",
			String.class,
			profileId));
		assertEquals(List.of("INSTAGRAM:@koready_demo"), jdbcTemplate.queryForList(
			"""
			SELECT CONCAT(link_type, ':', link_value) FROM buddy_social_links
			WHERE profile_id = ? ORDER BY display_order
			""",
			String.class,
			profileId));
		assertEquals(0, jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*) FROM buddy_blocks
			WHERE blocker_user_id = ? OR blocked_user_id = ?
			""",
			Integer.class,
			demoUserId,
			demoUserId));
		assertTrue(Files.readString(SEED, StandardCharsets.UTF_8)
			.contains("local_buddy_profile_id"));
	}

	private void runSeed() throws Exception {
		var resource = new FileSystemResource(SEED);
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

	private long insertViewer() {
		jdbcTemplate.update(
			"INSERT INTO users (public_id, signup_status) VALUES ('local-seed-viewer', 'COMPLETED')");
		return userId("local-seed-viewer");
	}

	private long userId(String publicId) {
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private long profileId(long userId) {
		return jdbcTemplate.queryForObject(
			"SELECT id FROM buddy_profiles WHERE user_id = ?", Long.class, userId);
	}

	private int count(String table, String column, Object value) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
			Integer.class,
			value);
	}
}
