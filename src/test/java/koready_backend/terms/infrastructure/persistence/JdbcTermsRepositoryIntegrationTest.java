package koready_backend.terms.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Instant;
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

import koready_backend.terms.application.TermsService;
import koready_backend.terms.application.TermsService.AgreementCommand;
import koready_backend.terms.application.exception.InvalidTermAgreementException;
import koready_backend.terms.application.exception.RequiredTermsNotAgreedException;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcTermsRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private TermsService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void advancesANewUserWhenNoTermsHaveBeenConfigured() {
		user("usr_no_terms");

		var required = service.getRequiredTerms("usr_no_terms");
		var updated = service.updateAgreements("usr_no_terms", List.of());

		assertEquals(List.of(), required.terms());
		assertEquals(true, required.allRequiredAgreed());
		assertEquals("LANGUAGE", updated.nextStep().name());
		assertEquals("NEED_LANGUAGE", signupStatus("usr_no_terms"));
	}

	@Test
	void selectsOnlyTheLatestPublishedEffectiveVersionAndStoresAgreement() {
		long userId = user("usr_current_terms");
		long termId = term("SERVICE_TERMS", 1);
		Instant now = Instant.now();
		long oldVersionId = version(
			termId, "1.0", true, now.minusSeconds(7200), now.minusSeconds(7200));
		long currentVersionId = version(
			termId, "2.0", true, now.minusSeconds(3600), now.minusSeconds(3600));
		version(
			termId, "3.0", true, now.plusSeconds(3600), now.minusSeconds(60));
		draftVersion(termId, "4.0", now.minusSeconds(60));

		var required = service.getRequiredTerms("usr_current_terms");

		assertEquals(1, required.terms().size());
		assertEquals(currentVersionId, required.terms().getFirst().termVersionId());
		assertEquals("2.0", required.terms().getFirst().version());
		assertEquals(false, required.allRequiredAgreed());

		var updated = service.updateAgreements(
			"usr_current_terms",
			List.of(new AgreementCommand(currentVersionId, true)));

		assertEquals(true, updated.allRequiredAgreed());
		assertEquals("NEED_LANGUAGE", signupStatus("usr_current_terms"));
		assertEquals(1, jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM user_term_agreements
			WHERE user_id = ? AND term_version_id = ? AND agreed = TRUE
			""",
			Integer.class,
			userId,
			currentVersionId));
		assertEquals(0, jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM user_term_agreements
			WHERE user_id = ? AND term_version_id = ?
			""",
			Integer.class,
			userId,
			oldVersionId));
	}

	@Test
	void rejectsMissingRequiredTermsAndPastVersionIdsAtomically() {
		long userId = user("usr_rejected_terms");
		long termId = term("PRIVACY_POLICY", 1);
		Instant now = Instant.now();
		long oldVersionId = version(
			termId, "1.0", true, now.minusSeconds(7200), now.minusSeconds(7200));
		version(
			termId, "2.0", true, now.minusSeconds(3600), now.minusSeconds(3600));

		assertThrows(RequiredTermsNotAgreedException.class,
			() -> service.updateAgreements("usr_rejected_terms", List.of()));
		assertThrows(InvalidTermAgreementException.class,
			() -> service.updateAgreements(
				"usr_rejected_terms",
				List.of(new AgreementCommand(oldVersionId, true))));

		assertEquals("NEED_TERMS", signupStatus("usr_rejected_terms"));
		assertEquals(0, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_term_agreements WHERE user_id = ?",
			Integer.class,
			userId));
	}

	private long user(String publicId) {
		jdbcTemplate.update(
			"""
			INSERT INTO users (public_id, signup_status)
			VALUES (?, 'NEED_TERMS')
			""",
			publicId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?",
			Long.class,
			publicId);
	}

	private long term(String code, int displayOrder) {
		jdbcTemplate.update(
			"""
			INSERT INTO term_definitions (code, display_order)
			VALUES (?, ?)
			""",
			code,
			displayOrder);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM term_definitions WHERE code = ?",
			Long.class,
			code);
	}

	private long version(
		long termId,
		String version,
		boolean required,
		Instant effectiveAt,
		Instant publishedAt
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO term_versions
			    (term_id, version_label, title, content_url, required,
			     effective_at, published_at)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			""",
			termId,
			version,
			"약관 " + version,
			"https://koready.cloud/terms/" + version,
			required,
			Timestamp.from(effectiveAt),
			Timestamp.from(publishedAt));
		return versionId(termId, version);
	}

	private void draftVersion(long termId, String version, Instant effectiveAt) {
		jdbcTemplate.update(
			"""
			INSERT INTO term_versions
			    (term_id, version_label, title, required, effective_at)
			VALUES (?, ?, ?, TRUE, ?)
			""",
			termId,
			version,
			"초안 " + version,
			Timestamp.from(effectiveAt));
	}

	private long versionId(long termId, String version) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT id FROM term_versions
			WHERE term_id = ? AND version_label = ?
			""",
			Long.class,
			termId,
			version);
	}

	private String signupStatus(String publicId) {
		return jdbcTemplate.queryForObject(
			"SELECT signup_status FROM users WHERE public_id = ?",
			String.class,
			publicId);
	}
}
