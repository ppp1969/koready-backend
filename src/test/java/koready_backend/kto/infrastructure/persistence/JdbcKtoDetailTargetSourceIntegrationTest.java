package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.kto.application.port.KtoDetailTargetSource;
import koready_backend.kto.domain.KtoDetailTarget;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcKtoDetailTargetSourceIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private KtoDetailTargetSource source;

	@Test
	void prioritizesTrustedEnglishMatchesWithoutTreatingAiAsTrusted() {
		long unmatched = place("unmatched");
		long ai = place("ai");
		localize(ai, "AI_TRANSLATED");
		long ktoEnglish = place("kto-en");
		localize(ktoEnglish, "KTO_EN");
		long manuallyEdited = place("manual-en");
		localize(manuallyEdited, "MANUAL_EDITED");

		List<KtoDetailTarget> targets = source.findAfter(0L, 3);

		assertEquals(
			List.of(ktoEnglish, manuallyEdited, unmatched),
			targets.stream().map(KtoDetailTarget::placeId).toList());
		assertTrue(source.existsAfter(0L));
	}

	private long place(String contentId) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, kto_content_type_id, active, show_flag)
			VALUES (?, '12', TRUE, FALSE)
			""",
			contentId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			contentId);
	}

	private void localize(long placeId, String source) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, translation_source)
			VALUES (?, 'EN', ?, ?)
			""",
			placeId,
			"English " + placeId,
			source);
	}
}
