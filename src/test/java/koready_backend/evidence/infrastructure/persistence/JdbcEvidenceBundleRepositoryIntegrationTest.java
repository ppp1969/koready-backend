package koready_backend.evidence.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import koready_backend.evidence.application.port.EvidenceBundleRepository;
import koready_backend.evidence.application.port.EvidenceBundleRepository.CompletionRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.CreateRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.ExclusionRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.ManifestFileRecord;
import koready_backend.evidence.domain.EvidenceBundleStatus;
import koready_backend.externalapi.domain.ExternalApiProvider;

@Tag("integration")
@SpringBootTest(properties = "koready.evidence-bundle.worker.poll-delay=PT1H")
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcEvidenceBundleRepositoryIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-07-19T09:00:00.123456Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired EvidenceBundleRepository repository;
	@Autowired JdbcTemplate jdbcTemplate;

	@Test
	void persistsLifecycleManifestExclusionsAndAudits() {
		var created = repository.create(new CreateRecord(
			"evidence_01J2ABCDEF", "2026 공모전 OpenAPI 사용 증빙",
			NOW.minusSeconds(3600), NOW, List.of(ExternalApiProvider.KTO, ExternalApiProvider.TMAP),
			List.of("searchFestival2"), true, 3, "admin-1", NOW));

		assertEquals(EvidenceBundleStatus.QUEUED, created.status());
		assertEquals(List.of(ExternalApiProvider.KTO, ExternalApiProvider.TMAP), created.providers());
		assertEquals(List.of("searchFestival2"), created.operations());
		assertTrue(repository.findPage(null, 10).stream().anyMatch(row -> row.bundleId().equals(created.bundleId())));

		var claimed = repository.claimNextQueued(NOW.plusSeconds(1)).orElseThrow();
		assertEquals(EvidenceBundleStatus.RUNNING, claimed.status());
		assertTrue(repository.claimNextQueued(NOW.plusSeconds(2)).isEmpty());

		repository.complete(new CompletionRecord(
			created.bundleId(), "evidence-bundles/evidence_01J2ABCDEF/file.zip",
			"koready-openapi-evidence-202607.zip", "a".repeat(64), 1024, 5, 1,
			List.of(new ExclusionRecord(ExternalApiProvider.TMAP, "PROVIDER_RETENTION_RESTRICTED")),
			List.of(new ManifestFileRecord("manifest.json", "b".repeat(64), 128)), NOW.plusSeconds(3)));
		repository.recordAudit(new EvidenceBundleRepository.AuditRecord(
			"admin-1", "EVIDENCE_BUNDLE_COMPLETED", created.bundleId(),
			java.util.Map.of("sha256", "a".repeat(64)), NOW.plusSeconds(3)));

		var completed = repository.findByBundleId(created.bundleId()).orElseThrow();
		assertEquals(EvidenceBundleStatus.COMPLETED, completed.status());
		assertEquals(1024L, completed.byteSize());
		assertEquals(1, completed.exclusions().size());
		assertEquals("manifest.json", completed.manifestFiles().getFirst().path());
		assertEquals(1L, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM admin_audit_logs WHERE resource_type = 'EVIDENCE_BUNDLE'", Long.class));
	}
}
