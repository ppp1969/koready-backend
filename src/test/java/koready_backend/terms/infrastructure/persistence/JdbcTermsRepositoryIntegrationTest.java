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
import koready_backend.terms.domain.TermContentFormat;

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

	@Autowired
	private koready_backend.auth.application.port.AuthRepository authRepository;

	@Autowired
	private koready_backend.user.application.UserLanguageService languageService;

	@Test
	void newGoogleUsersSelectKoreanOrEnglishBeforeReadingAndAcceptingTerms() {
		long termId = term("LANGUAGE_FLOW_TERMS", 1);
		Instant now = Instant.now();
		long versionId = version(termId, "1.0", true, now.minusSeconds(60), now.minusSeconds(60));
		for (var language : koready_backend.place.domain.PlaceLanguage.values()) {
			String content = language.name().equals("KO") ? "한국어 약관" : "English terms";
			jdbcTemplate.update("""
				INSERT INTO term_version_localizations
				    (term_version_id, language, title, content_body, content_format, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'PLAIN_TEXT', NOW(6), NOW(6))
				""", versionId, language.name(), content, content);
			String publicId = "usr_language_flow_" + language;
			var user = authRepository.createGoogleUser(
				new koready_backend.auth.domain.GoogleIdentity("language-flow-" + language, "flow@example.com"),
				publicId, now);
			assertEquals("LANGUAGE", user.signupStatus().nextStep().name());
			assertEquals("TERMS", languageService.update(publicId, language).nextStep().name());
			assertEquals(content, service.getRequiredTerms(publicId).terms().getFirst().content());
			assertThrows(RequiredTermsNotAgreedException.class,
				() -> service.updateAgreements(publicId, List.of()));
			assertEquals("NEED_TERMS", signupStatus(publicId));
			assertEquals("ONBOARDING", service.updateAgreements(publicId,
				List.of(new AgreementCommand(versionId, true))).nextStep().name());
			assertEquals("ONBOARDING", authRepository.findByGoogleSubject("language-flow-" + language)
				.orElseThrow().signupStatus().nextStep().name());
		}
	}

	@Test
	void advancesANewUserWhenNoTermsHaveBeenConfigured() {
		user("usr_no_terms");

		var required = service.getRequiredTerms("usr_no_terms");
		var updated = service.updateAgreements("usr_no_terms", List.of());

		assertEquals(List.of(), required.terms());
		assertEquals(true, required.allRequiredAgreed());
		assertEquals("ONBOARDING", updated.nextStep().name());
		assertEquals("NEED_ONBOARDING", signupStatus("usr_no_terms"));
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
		assertEquals("NEED_ONBOARDING", signupStatus("usr_current_terms"));
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

	@Test
	void readsPublishedInlineMarkdownAsTheCurrentTerm() {
		long userId = user("usr_inline_terms");
		long termId = term("SERVICE_TERMS", 1);
		Instant now = Instant.now();
		String content = "# 약관\n본문";
		jdbcTemplate.update("""
			INSERT INTO term_versions
			    (term_id, version_label, title, content_body, content_format, required,
			     effective_at, published_at)
			VALUES (?, '1.0', '서비스 이용약관', ?, 'MARKDOWN', TRUE, ?, ?)
			""", termId, content, Timestamp.from(now.minusSeconds(60)),
			Timestamp.from(now.minusSeconds(60)));

		var terms = service.getRequiredTerms("usr_inline_terms").terms();

		assertEquals(1, terms.size());
		assertEquals(userId > 0, true);
		assertEquals(content, terms.getFirst().content());
		assertEquals(TermContentFormat.MARKDOWN, terms.getFirst().contentFormat());
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
